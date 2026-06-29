package orange.wz;

import orange.wz.provider.WzImageFile;
import orange.wz.provider.tools.MediaExportType;
import orange.wz.provider.tools.XmlExport;

import java.io.IOException;
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
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * 批量将 .img 二进制文件转换为 XML 格式，供服务端读取。
 * 用法：java -cp xxx orange.wz.BatchImg2Xml &lt;源目录&gt; &lt;目标目录&gt;
 * 两个参数均为必填。支持 Ctrl+C 或 SIGTERM 优雅中断。
 */
public class BatchImg2Xml {

    /** 全局取消标志，由 shutdownHook 设置 */
    private static final AtomicBoolean cancelled = new AtomicBoolean(false);

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("用法: java -cp xxx orange.wz.BatchImg2Xml <源目录> <目标目录>");
            System.out.println("  源目录: 包含 .img 文件的数据目录");
            System.out.println("  目标目录: 输出 .img.xml 文件的目录");
            System.exit(1);
        }
        Path src = Path.of(args[0]);
        Path dst = Path.of(args[1]);

        if (!Files.exists(src)) {
            System.err.println("源目录不存在: " + src);
            System.exit(1);
        }
        Files.createDirectories(dst);

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

        // 第一步：先收集所有 .img 文件路径（轻量 I/O，单线程即可）
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

        System.out.println("共发现 " + imgFiles.size() + " 个 .img 文件，开始转换...");

        // 第二步：多线程并行转换，支持中断取消
        int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors());
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        try {
            for (Path file : imgFiles) {
                if (cancelled.get()) break;
                pool.submit(() -> convertOne(src, dst, file, success, failure));
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

        long elapsed = System.currentTimeMillis() - startMs;
        System.out.printf("%n转换完成！成功: %d  失败: %d  耗时: %.1fs%n",
                success.get(), failure.get(), elapsed / 1000.0);
    }

    private static void convertOne(Path src, Path dst, Path file, AtomicInteger success, AtomicInteger failure) {
        // 快速响应取消信号
        if (cancelled.get()) return;

        String name = file.getFileName().toString();
        Path rel = src.relativize(file);
        Path outFile = dst.resolve(rel.toString() + ".xml");

        try {
            Files.createDirectories(outFile.getParent());
        } catch (IOException e) {
            System.err.println("创建目录失败: " + outFile.getParent() + " -> " + e.getMessage());
            failure.incrementAndGet();
            return;
        }

        WzImageFile imgFile = new WzImageFile(name, file.toString(), "国际服务器(低版本)", WZ_GMS_IV, DEFAULT_KEY);
        try {
            if (cancelled.get()) return;
            if (!imgFile.parse()) {
                System.err.println("[FAIL] 解析失败: " + rel);
                failure.incrementAndGet();
                return;
            }

            if (cancelled.get()) return;
            boolean ok = new XmlExport(imgFile, 2, false, MediaExportType.NONE).export(outFile);
            if (ok) {
                System.out.println("[OK] " + rel);
                success.incrementAndGet();
            } else {
                System.err.println("[FAIL] 导出XML失败: " + rel);
                failure.incrementAndGet();
            }
        } finally {
            imgFile.unparse();
        }
    }
}
