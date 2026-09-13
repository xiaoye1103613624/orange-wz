import orange.wz.provider.WzDirectory;
import orange.wz.provider.WzFolder;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.WzObject;
import orange.wz.provider.properties.WzCanvasProperty;
import orange.wz.provider.properties.WzPngFormat;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * 扫描目录内 .img：解析失败、空壳(&lt;200B)、canvas 格式分布。
 * 默认标红 v083 不支持的格式（ARGB1555/DXT5/BC7）以及不支持 scale 却 scale≠0 的画布。
 * 用法: java -cp ... ScanImgFormats &lt;源目录&gt; [maxFlagged=50]
 */
public class ScanImgFormats {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("用法: java ScanImgFormats <源目录> [maxFlagged]");
            System.exit(1);
        }
        Path src = Path.of(args[0]);
        int maxFlagged = args.length > 1 ? Integer.parseInt(args[1]) : 50;
        Set<String> flag = Set.of(
                WzPngFormat.ARGB1555.name(),
                WzPngFormat.DXT5.name(),
                WzPngFormat.BC7.name()
        );

        List<Path> imgs = new ArrayList<>();
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().endsWith(".img")) imgs.add(file);
                return FileVisitResult.CONTINUE;
            }
        });

        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        AtomicInteger tiny = new AtomicInteger();
        EnumMap<WzPngFormat, AtomicInteger> byFmt = new EnumMap<>(WzPngFormat.class);
        for (WzPngFormat f : WzPngFormat.values()) byFmt.put(f, new AtomicInteger());
        List<String> failList = Collections.synchronizedList(new ArrayList<>());
        List<String> tinyList = Collections.synchronizedList(new ArrayList<>());
        List<String> flagged = Collections.synchronizedList(new ArrayList<>());

        System.out.println("扫描 " + imgs.size() + " 个 .img @ " + src);
        imgs.parallelStream().forEach(file -> {
            long size;
            try { size = Files.size(file); } catch (IOException e) { size = -1; }
            Path rel = src.relativize(file);
            if (size >= 0 && size < 200) {
                tiny.incrementAndGet();
                if (tinyList.size() < 100) tinyList.add(rel + " (" + size + "B)");
            }
            String name = file.getFileName().toString();
            WzImageFile img = new WzImageFile(name, file.toString(), "国际服务器(低版本)", WZ_GMS_IV, DEFAULT_KEY);
            try {
                if (!img.parse()) {
                    fail.incrementAndGet();
                    if (failList.size() < 200) failList.add(rel.toString());
                    return;
                }
                ok.incrementAndGet();
                walk(img, rel.toString(), byFmt, flagged, flag, maxFlagged);
            } finally {
                img.unparse();
            }
        });

        System.out.println("=== 解析 ===");
        System.out.println("ok=" + ok.get() + " fail=" + fail.get() + " tiny(<200B)=" + tiny.get());
        if (!failList.isEmpty()) {
            System.out.println("--- parse fail (cap) ---");
            failList.forEach(s -> System.out.println("[FAIL] " + s));
        }
        if (!tinyList.isEmpty()) {
            System.out.println("--- tiny shells (cap) ---");
            tinyList.forEach(s -> System.out.println("[TINY] " + s));
        }
        System.out.println("=== canvas 格式 ===");
        byFmt.forEach((k, v) -> {
            if (v.get() > 0) System.out.println(k + "=" + v.get());
        });
        if (!flagged.isEmpty()) {
            System.out.println("--- flagged unsupported/invalid canvas (cap) ---");
            flagged.forEach(s -> System.out.println("[FLAG] " + s));
        }
        System.out.println(fail.get() == 0 && flagged.isEmpty() ? "RESULT: CLEAN" : "RESULT: ISSUES");
    }

    private static void walk(
            WzObject cur, String rootRel,
            EnumMap<WzPngFormat, AtomicInteger> byFmt,
            List<String> flagged, Set<String> flag, int maxFlagged
    ) {
        if (cur instanceof WzCanvasProperty canvas) {
            WzPngFormat fmt = canvas.getFormat();
            if (fmt != null) {
                byFmt.get(fmt).incrementAndGet();
                boolean flagByFormat = flag.contains(fmt.name());
                boolean flagByScale = !fmt.supportsScale() && canvas.getScale() != 0;
                if ((flagByFormat || flagByScale) && flagged.size() < maxFlagged) {
                    String reason = flagByScale ? "scale_not_allowed" : "unsupported_v083";
                    flagged.add(rootRel + " :: " + canvas.getName()
                            + " " + canvas.getWidth() + "x" + canvas.getHeight()
                            + " " + fmt + " scale=" + canvas.getScale()
                            + " " + reason);
                }
            }
        }
        for (WzObject child : childrenOf(cur)) {
            walk(child, rootRel, byFmt, flagged, flag, maxFlagged);
        }
    }

    private static List<WzObject> childrenOf(WzObject parent) {
        if (parent instanceof WzFolder folder) return folder.getChildren();
        if (parent instanceof WzDirectory dir) return dir.getChildren();
        if (parent instanceof WzImage image) return new ArrayList<>(image.getChildren());
        if (parent instanceof WzImageProperty prop && prop.isListProperty()) return new ArrayList<>(prop.getChildren());
        return List.of();
    }
}
