package orange.wz.provider.tools.keyconvert;

import orange.wz.provider.WzDirectory;
import orange.wz.provider.WzFile;
import orange.wz.provider.WzImageFile;
import orange.wz.provider.tools.wzkey.WzKey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Safe parallel WZ/IMG key conversion with optional content fingerprint verification.
 * Each file uses isolated reader/writer state — safe under a single MCP HTTP port.
 */
public final class KeyConvertService {
    public record ConvertRequest(
            Path sourceRoot,
            Path outputDir,
            WzKey sourceKey,
            WzKey targetKey,
            Short targetWzVersion,
            int parallelism,
            boolean verify,
            boolean overwrite
    ) {
        public ConvertRequest {
            Objects.requireNonNull(sourceRoot, "sourceRoot");
            Objects.requireNonNull(outputDir, "outputDir");
            Objects.requireNonNull(sourceKey, "sourceKey");
            Objects.requireNonNull(targetKey, "targetKey");
            if (parallelism < 1) {
                parallelism = Math.max(1, Runtime.getRuntime().availableProcessors());
            }
        }
    }

    public record FileResult(
            String relativePath,
            boolean success,
            String message,
            String fingerprint
    ) {
    }

    public record BatchResult(
            int total,
            int success,
            int failure,
            long elapsedMs,
            List<FileResult> results
    ) {
    }

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public void cancel() {
        cancelled.set(true);
    }

    public BatchResult convertTree(ConvertRequest request) throws IOException {
        cancelled.set(false);
        Path sourceRoot = request.sourceRoot().toAbsolutePath().normalize();
        Path outputDir = request.outputDir().toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IOException("sourceRoot is not a directory: " + sourceRoot);
        }
        Files.createDirectories(outputDir);

        List<Path> files = new ArrayList<>();
        try (var walk = Files.walk(sourceRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(KeyConvertService::isConvertible)
                    .forEach(files::add);
        }

        return convertFiles(files, sourceRoot, request);
    }

    public BatchResult convertFiles(List<Path> files, Path sourceRoot, ConvertRequest request) throws IOException {
        cancelled.set(false);
        Path root = sourceRoot.toAbsolutePath().normalize();
        Path outputDir = request.outputDir().toAbsolutePath().normalize();
        Files.createDirectories(outputDir);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        List<FileResult> results = new ArrayList<>();
        Object resultsLock = new Object();
        long start = System.currentTimeMillis();

        int workers = Math.max(1, request.parallelism());
        ExecutorService pool = Executors.newFixedThreadPool(workers, r -> {
            Thread t = new Thread(r, "key-convert-worker");
            t.setDaemon(true);
            return t;
        });
        try {
            List<Future<?>> futures = new ArrayList<>(files.size());
            for (Path file : files) {
                futures.add(pool.submit(() -> {
                    FileResult result = convertOne(file, root, outputDir, request);
                    synchronized (resultsLock) {
                        results.add(result);
                    }
                    if (result.success()) {
                        success.incrementAndGet();
                    } else {
                        failure.incrementAndGet();
                    }
                }));
            }
            for (Future<?> future : futures) {
                if (cancelled.get()) {
                    future.cancel(true);
                    continue;
                }
                try {
                    future.get();
                } catch (Exception e) {
                    // convertOne already records failures; interrupted cancel is expected
                }
            }
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                pool.shutdownNow();
            }
        }

        return new BatchResult(files.size(), success.get(), failure.get(),
                System.currentTimeMillis() - start, List.copyOf(results));
    }

    private FileResult convertOne(Path file, Path sourceRoot, Path outputDir, ConvertRequest request) {
        if (cancelled.get()) {
            return new FileResult(rel(sourceRoot, file), false, "cancelled", null);
        }
        String relative = rel(sourceRoot, file);
        Path outFile = outputDir.resolve(relative).normalize();
        if (!outFile.startsWith(outputDir)) {
            return new FileResult(relative, false, "output path escapes outputDir", null);
        }

        try {
            Files.createDirectories(outFile.getParent());
            if (Files.exists(outFile) && !request.overwrite()) {
                return new FileResult(relative, false, "output exists (overwrite=false)", null);
            }

            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".img")) {
                return convertImg(file, outFile, relative, request);
            }
            if (name.endsWith(".wz")) {
                return convertWz(file, outFile, relative, request);
            }
            return new FileResult(relative, false, "unsupported extension", null);
        } catch (Exception e) {
            return new FileResult(relative, false, "exception: " + e.getMessage(), null);
        }
    }

    private FileResult convertImg(Path src, Path dst, String relative, ConvertRequest request) {
        WzKey sk = request.sourceKey();
        WzKey tk = request.targetKey();
        WzImageFile img = new WzImageFile(src.getFileName().toString(), src.toString(),
                sk.getName(), sk.getIv(), sk.getUserKey());
        try {
            if (!img.parse()) {
                return new FileResult(relative, false, "source parse failed", null);
            }
            String before = request.verify() ? ContentFingerprint.ofImage(img) : null;
            if (!img.changeKey(tk.getName(), tk.getIv(), tk.getUserKey())) {
                return new FileResult(relative, false, "changeKey failed", null);
            }
            img.setFilePath(dst.toString());
            if (!img.save(dst)) {
                safeDelete(dst);
                return new FileResult(relative, false, "save failed", null);
            }
            if (request.verify()) {
                String after = verifyImg(dst, tk);
                if (!Objects.equals(before, after)) {
                    safeDelete(dst);
                    return new FileResult(relative, false,
                            "fingerprint mismatch before=" + before + " after=" + after, before);
                }
                return new FileResult(relative, true, "ok+verified", after);
            }
            return new FileResult(relative, true, "ok", null);
        } catch (Exception e) {
            safeDelete(dst);
            return new FileResult(relative, false, "exception: " + e.getMessage(), null);
        } finally {
            img.unparse();
        }
    }

    private FileResult convertWz(Path src, Path dst, String relative, ConvertRequest request) {
        WzKey sk = request.sourceKey();
        WzKey tk = request.targetKey();
        WzFile wz = new WzFile(src.toString(), (short) -1, sk.getName(), sk.getIv(), sk.getUserKey());
        try {
            if (!wz.parse()) {
                return new FileResult(relative, false, "source wz parse failed", null);
            }
            short version = request.targetWzVersion() != null
                    ? request.targetWzVersion()
                    : wz.getHeader().getFileVersion();
            WzDirectory dir = wz.getWzDirectory();
            String before = request.verify() ? ContentFingerprint.ofDirectory(dir) : null;
            if (!wz.changeKey(version, tk.getName(), tk.getIv(), tk.getUserKey())) {
                return new FileResult(relative, false, "wz changeKey failed", null);
            }
            wz.setFilePath(dst.toString());
            if (!wz.save()) {
                safeDelete(dst);
                return new FileResult(relative, false, "wz save failed", null);
            }
            if (request.verify()) {
                String after = verifyWz(dst, tk, version);
                if (!Objects.equals(before, after)) {
                    safeDelete(dst);
                    return new FileResult(relative, false,
                            "fingerprint mismatch before=" + before + " after=" + after, before);
                }
                return new FileResult(relative, true, "ok+verified", after);
            }
            return new FileResult(relative, true, "ok", null);
        } catch (Exception e) {
            safeDelete(dst);
            return new FileResult(relative, false, "exception: " + e.getMessage(), null);
        } finally {
            try {
                wz.clear();
            } catch (Exception ignored) {
            }
        }
    }

    private static String verifyImg(Path path, WzKey key) {
        WzImageFile check = new WzImageFile(path.getFileName().toString(), path.toString(),
                key.getName(), key.getIv(), key.getUserKey());
        try {
            if (!check.parse()) {
                throw new IllegalStateException("verify parse failed");
            }
            return ContentFingerprint.ofImage(check);
        } finally {
            check.unparse();
        }
    }

    private static String verifyWz(Path path, WzKey key, short version) {
        WzFile check = new WzFile(path.toString(), version, key.getName(), key.getIv(), key.getUserKey());
        try {
            if (!check.parse()) {
                throw new IllegalStateException("verify wz parse failed");
            }
            return ContentFingerprint.ofDirectory(check.getWzDirectory());
        } finally {
            try {
                check.clear();
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean isConvertible(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".img") || name.endsWith(".wz");
    }

    private static String rel(Path root, Path file) {
        return root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static void safeDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
