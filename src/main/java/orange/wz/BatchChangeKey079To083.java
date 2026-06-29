package orange.wz;

import orange.wz.provider.WzImageFile;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_CMS_IV;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * 将 079 客户端(.img, CMS/EMS 区密钥)批量转换为 083 客户端(.img, GMS 区密钥)。
 * 支持 Ctrl+C 或 SIGTERM 优雅中断。
 * 用法：java -cp xxx orange.wz.BatchChangeKey079To083 &lt;079源目录&gt; &lt;083输出目录&gt; &lt;异常日志路径&gt;
 */
public class BatchChangeKey079To083 {

    /** 全局取消标志 */
    private static final AtomicBoolean cancelled = new AtomicBoolean(false);

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("用法: java -cp xxx orange.wz.BatchChangeKey079To083 <079源目录> <083输出目录> [异常日志路径]");
            System.out.println("  079源目录: 包含079密钥 .img 文件的目录");
            System.out.println("  083输出目录: 输出083密钥 .img 文件的目录");
            System.out.println("  异常日志路径: 可选，默认 change_key_079_to_083_error.log");
            System.exit(1);
        }
        Path src = Path.of(args[0]);
        Path dst = Path.of(args[1]);
        Path logPath = Path.of(args.length > 2 ? args[2] : "change_key_079_to_083_error.log");

        if (!Files.exists(src)) {
            System.err.println("源目录不存在: " + src);
            System.exit(1);
        }
        Files.createDirectories(dst);

        List<Path> imgFiles = new ArrayList<>();
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (cancelled.get()) return FileVisitResult.TERMINATE;
                if (file.getFileName().toString().endsWith(".img")) {
                    imgFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        System.out.println("共发现 " + imgFiles.size() + " 个 .img 文件，开始转换密钥...");

        // 注册 Ctrl+C 优雅中断
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!cancelled.get()) {
                cancelled.set(true);
                System.out.println("\n收到中断信号，正在停止...");
            }
        }, "ShutdownHook"));

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        long startMs = System.currentTimeMillis();

        try (PrintWriter logWriter = new PrintWriter(Files.newBufferedWriter(logPath, StandardCharsets.UTF_8))) {
            Object logLock = new Object();

            // 每个 .img 文件相互独立(各自独立的 WzImageFile/Reader/Writer 实例)，可安全并行处理
            int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors());
            ExecutorService pool = Executors.newFixedThreadPool(parallelism);
            try {
                for (Path file : imgFiles) {
                    if (cancelled.get()) break;
                    pool.submit(() -> convertOne(src, dst, file, success, failure, logWriter, logLock));
                }
                pool.shutdown();
                // 轮询等待，每秒检查取消标志
                while (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                    if (cancelled.get()) {
                        pool.shutdownNow();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancelled.set(true);
                pool.shutdownNow();
            }
        }

        long elapsed = System.currentTimeMillis() - startMs;
        System.out.printf("%n密钥转换完成！成功: %d  失败: %d  耗时: %.1fs  异常日志: %s%n",
                success.get(), failure.get(), elapsed / 1000.0, logPath);
    }

    private static void convertOne(Path src, Path dst, Path file, AtomicInteger success, AtomicInteger failure,
                                    PrintWriter logWriter, Object logLock) {
        if (cancelled.get()) return;
        String name = file.getFileName().toString();
        Path rel = src.relativize(file);
        Path outFile = dst.resolve(rel);

        try {
            Files.createDirectories(outFile.getParent());
        } catch (IOException e) {
            logError(logWriter, logLock, rel, "创建目录失败: " + e.getMessage());
            failure.incrementAndGet();
            return;
        }

        WzImageFile imgFile = new WzImageFile(name, file.toString(), "079-CMS", WZ_CMS_IV, DEFAULT_KEY);
        try {
            if (cancelled.get()) return;
            if (!imgFile.parse()) {
                logError(logWriter, logLock, rel, "079密钥解析失败(可能不是079标准格式)");
                failure.incrementAndGet();
                return;
            }

            if (cancelled.get()) return;
            imgFile.changeKey("083-GMS", WZ_GMS_IV, DEFAULT_KEY);
            // changeKey 内部已将 filePath 保留为原 079 文件路径，这里改为输出路径再保存，避免覆盖源文件
            imgFile.setFilePath(outFile.toString());
            boolean ok = imgFile.save(outFile);
            if (ok) {
                success.incrementAndGet();
            } else {
                logError(logWriter, logLock, rel, "保存083密钥文件失败");
                failure.incrementAndGet();
            }
        } catch (Exception e) {
            logError(logWriter, logLock, rel, "转换异常: " + e);
            failure.incrementAndGet();
        } finally {
            imgFile.unparse();
        }
    }

    private static void logError(PrintWriter logWriter, Object logLock, Path rel, String message) {
        synchronized (logLock) {
            logWriter.println(rel + " => " + message);
            logWriter.flush();
        }
        System.err.println("[FAIL] " + rel + " => " + message);
    }
}
