package orange.wz;

import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * 将转换好的083密钥数据与现有 BeiDou-Client/Data 目录按节点级别合并。
 * 支持 Ctrl+C 或 SIGTERM 优雅中断。
 * 用法：java -cp xxx orange.wz.BatchMergeNodes &lt;源目录&gt; &lt;目标目录&gt; &lt;异常日志路径&gt;
 */
public class BatchMergeNodes {

    private static final String CLASSPATH = System.getProperty("java.class.path");
    private static final String JAVA_BIN = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

    /** 全局取消标志 */
    private static final AtomicBoolean cancelled = new AtomicBoolean(false);

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("用法: java -cp xxx orange.wz.BatchMergeNodes <源目录> <目标目录> [异常日志路径]");
            System.out.println("  源目录: 转换后的083数据目录");
            System.out.println("  目标目录: BeiDou-Client/Data 目录");
            System.out.println("  异常日志路径: 可选，默认 merge_nodes_anomaly.log");
            System.exit(1);
        }
        Path src = Path.of(args[0]);
        Path dst = Path.of(args[1]);
        Path logPath = Path.of(args.length > 2 ? args[2] : "merge_nodes_anomaly.log");

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
        System.out.println("共发现 " + imgFiles.size() + " 个 .img 文件，开始按节点合并...");

        // 注册 Ctrl+C 优雅中断
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!cancelled.get()) {
                cancelled.set(true);
                System.out.println("\n收到中断信号，正在停止...");
            }
        }, "ShutdownHook"));

        AtomicInteger copied = new AtomicInteger();
        AtomicInteger merged = new AtomicInteger();
        AtomicInteger skippedSame = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();
        AtomicInteger addedNodeTotal = new AtomicInteger();
        long startMs = System.currentTimeMillis();

        try (PrintWriter logWriter = new PrintWriter(Files.newBufferedWriter(logPath, StandardCharsets.UTF_8))) {
            Object logLock = new Object();

            // 部分地图/UI文件解析后体积很大，并发数过高容易把堆内存占满导致进程被系统杀掉，这里降低并发
            int parallelism = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors()));
            ExecutorService pool = Executors.newFixedThreadPool(parallelism);
            AtomicInteger processedCount = new AtomicInteger();
            int total = imgFiles.size();
            try {
                for (Path file : imgFiles) {
                    if (cancelled.get()) break;
                    pool.submit(() -> {
                        try {
                            Path rel = src.relativize(file);
                            // Sound目录下部分音频文件解析头信息时可能触发JDK音频库底层原生崩溃(JVM直接挂掉，
                            // Java层try/catch无法拦截)，已通过上一轮运行实测确认(Sound/BgmGL.img)，
                            // 这类文件改为用独立子进程处理：子进程崩溃不会影响本进程，可继续处理后续文件
                            boolean risky = isRiskySoundFile(rel);
                            if (risky) {
                                processOneInSubProcess(src, dst, file, rel, copied, merged, skippedSame,
                                        errorCount, addedNodeTotal, logWriter, logLock);
                            } else {
                                processOne(src, dst, file, copied, merged, skippedSame, errorCount,
                                        addedNodeTotal, logWriter, logLock);
                            }
                        } catch (OutOfMemoryError oom) {
                            logAnomaly(logWriter, logLock, src.relativize(file), "内存不足(OOM)，跳过此文件: " + oom.getMessage());
                            errorCount.incrementAndGet();
                        } catch (Exception e) {
                            logAnomaly(logWriter, logLock, src.relativize(file), "处理异常: " + e);
                            errorCount.incrementAndGet();
                        } finally {
                            int done = processedCount.incrementAndGet();
                            if (done % 1000 == 0) {
                                System.out.println("进度: " + done + "/" + total);
                            }
                        }
                    });
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
        System.out.printf("%n合并完成！直接复制: %d  按节点合并: %d(共补充%d个节点)  内容相同跳过: %d  异常: %d  耗时: %.1fs  异常日志: %s%n",
                copied.get(), merged.get(), addedNodeTotal.get(), skippedSame.get(), errorCount.get(), elapsed / 1000.0, logPath);
    }

    private static void processOne(Path src, Path dst, Path srcFile, AtomicInteger copied, AtomicInteger merged,
                                    AtomicInteger skippedSame, AtomicInteger errorCount, AtomicInteger addedNodeTotal,
                                    PrintWriter logWriter, Object logLock) {
        if (cancelled.get()) return;
        Path rel = src.relativize(srcFile);
        Path dstFile = dst.resolve(rel);

        try {
            if (!Files.exists(dstFile)) {
                Files.createDirectories(dstFile.getParent());
                Files.copy(srcFile, dstFile, StandardCopyOption.REPLACE_EXISTING);
                copied.incrementAndGet();
                return;
            }

            if (cancelled.get()) return;
            String name = srcFile.getFileName().toString();
            WzImageFile srcImg = new WzImageFile(name, srcFile.toString(), "083-GMS", WZ_GMS_IV, DEFAULT_KEY);
            WzImageFile dstImg = new WzImageFile(name, dstFile.toString(), "083-GMS", WZ_GMS_IV, DEFAULT_KEY);
            try {
                if (!srcImg.parse()) {
                    logAnomaly(logWriter, logLock, rel, "源文件解析失败，跳过比较");
                    errorCount.incrementAndGet();
                    return;
                }
                if (cancelled.get()) return;
                if (!dstImg.parse()) {
                    logAnomaly(logWriter, logLock, rel, "目标文件解析失败，跳过比较");
                    errorCount.incrementAndGet();
                    return;
                }

                if (cancelled.get()) return;
                int added = mergeTopLevel(srcImg, dstImg);
                if (added > 0) {
                    if (cancelled.get()) return;
                    boolean ok = dstImg.save(dstFile);
                    if (ok) {
                        merged.incrementAndGet();
                        addedNodeTotal.addAndGet(added);
                    } else {
                        logAnomaly(logWriter, logLock, rel, "补充了" + added + "个节点但保存失败");
                        errorCount.incrementAndGet();
                    }
                } else {
                    skippedSame.incrementAndGet();
                }
            } finally {
                srcImg.unparse();
                dstImg.unparse();
            }
        } catch (Exception e) {
            logAnomaly(logWriter, logLock, rel, "处理异常: " + e);
            errorCount.incrementAndGet();
        }
    }

    // 顶层(WzImage)节点合并：按名称比较，缺失则深拷贝补充，已存在则不覆盖(若双方都是列表节点则递归比较子节点)
    private static int mergeTopLevel(WzImage srcImg, WzImage dstImg) {
        int added = 0;
        for (WzImageProperty srcChild : srcImg.getChildren()) {
            if (cancelled.get()) break;
            WzImageProperty dstChild = dstImg.getChild(srcChild.getName());
            if (dstChild == null) {
                WzImageProperty clone = srcChild.deepClone(dstImg);
                if (dstImg.addChild(clone)) {
                    clone.setWzImage(dstImg);
                    clone.setChildrenWzImage(dstImg);
                    added++;
                }
            } else if (srcChild.isListProperty() && dstChild.isListProperty()) {
                added += mergeProperty(srcChild, dstChild, dstImg);
            }
            // 名称相同但非列表节点(叶子节点)：视为已存在，跳过，不覆盖内容
        }
        return added;
    }

    // 子节点(WzImageProperty)级别的递归合并
    private static int mergeProperty(WzImageProperty src, WzImageProperty dst, WzImage dstWzImage) {
        int added = 0;
        for (WzImageProperty srcChild : src.getChildren()) {
            if (cancelled.get()) break;
            WzImageProperty dstChild = dst.getChild(srcChild.getName());
            if (dstChild == null) {
                WzImageProperty clone = srcChild.deepClone(dst);
                if (dst.addChild(clone)) {
                    clone.setWzImage(dstWzImage);
                    clone.setChildrenWzImage(dstWzImage);
                    added++;
                }
            } else if (srcChild.isListProperty() && dstChild.isListProperty()) {
                added += mergeProperty(srcChild, dstChild, dstWzImage);
            }
        }
        return added;
    }

    // Sound目录下的.img文件包含音频头解析，曾在实测中触发JDK音频库底层原生崩溃(进程直接消失，无异常堆栈)，全部隔离到子进程处理
    private static boolean isRiskySoundFile(Path rel) {
        for (Path part : rel) {
            if (part.toString().equalsIgnoreCase("Sound")) return true;
        }
        return false;
    }

    // 用独立子进程处理单个文件：子进程若发生原生层崩溃，只会导致该子进程退出，不会影响本批处理主进程
    private static void processOneInSubProcess(Path src, Path dst, Path srcFile, Path rel, AtomicInteger copied,
                                                 AtomicInteger merged, AtomicInteger skippedSame, AtomicInteger errorCount,
                                                 AtomicInteger addedNodeTotal, PrintWriter logWriter, Object logLock) {
        Path dstFile = dst.resolve(rel);
        try {
            ProcessBuilder pb = new ProcessBuilder(JAVA_BIN, "-Xmx512m", "-cp", CLASSPATH,
                    "orange.wz.MergeOneFile", srcFile.toString(), dstFile.toString());
            pb.redirectErrorStream(false);
            Process process = pb.start();

            String lastLine = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) lastLine = line;
                }
            }
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logAnomaly(logWriter, logLock, rel, "子进程处理超时(>120s)，已强制终止，跳过此文件");
                errorCount.incrementAndGet();
                return;
            }

            if (lastLine == null) {
                logAnomaly(logWriter, logLock, rel, "子进程异常退出(退出码:" + process.exitValue() + ")，可能为底层原生崩溃，已跳过此文件");
                errorCount.incrementAndGet();
            } else if (lastLine.equals("COPIED")) {
                copied.incrementAndGet();
            } else if (lastLine.equals("SKIPPED_SAME")) {
                skippedSame.incrementAndGet();
            } else if (lastLine.startsWith("MERGED:")) {
                int added = Integer.parseInt(lastLine.substring("MERGED:".length()));
                merged.incrementAndGet();
                addedNodeTotal.addAndGet(added);
            } else if (lastLine.startsWith("ERROR:")) {
                logAnomaly(logWriter, logLock, rel, lastLine.substring("ERROR:".length()));
                errorCount.incrementAndGet();
            } else {
                logAnomaly(logWriter, logLock, rel, "子进程返回未知结果: " + lastLine);
                errorCount.incrementAndGet();
            }
        } catch (Exception e) {
            logAnomaly(logWriter, logLock, rel, "启动子进程异常: " + e);
            errorCount.incrementAndGet();
        }
    }

    private static void logAnomaly(PrintWriter logWriter, Object logLock, Path rel, String message) {
        synchronized (logLock) {
            logWriter.println(rel + " => " + message);
            logWriter.flush();
        }
        System.err.println("[WARN] " + rel + " => " + message);
    }
}
