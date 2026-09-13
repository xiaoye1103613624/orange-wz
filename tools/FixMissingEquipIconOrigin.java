import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.properties.WzCanvasProperty;
import orange.wz.provider.properties.WzVectorProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * Batch-add missing info/icon and info/iconRaw origin vectors for extended equip imgs.
 * Origin defaults to (0, height) so tip/inventory DrawItemIconForSlot anchors correctly.
 *
 * Usage:
 *   java -cp OrzRepacker.jar;tools FixMissingEquipIconOrigin &lt;list.txt|dir&gt; [--dry-run]
 */
public class FixMissingEquipIconOrigin {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: FixMissingEquipIconOrigin <list.txt|dir> [--dry-run]");
            System.exit(2);
        }
        boolean dry = false;
        List<Path> imgs = new ArrayList<>();
        for (String a : args) {
            if ("--dry-run".equals(a)) {
                dry = true;
                continue;
            }
            Path p = Path.of(a);
            if (Files.isDirectory(p)) {
                try (var stream = Files.list(p)) {
                    stream.filter(x -> x.getFileName().toString().endsWith(".img"))
                            .forEach(imgs::add);
                }
            } else if (Files.isRegularFile(p) && a.endsWith(".txt")) {
                for (String line : Files.readAllLines(p)) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        imgs.add(Path.of(line));
                    }
                }
            } else if (Files.isRegularFile(p) && a.endsWith(".img")) {
                imgs.add(p);
            }
        }
        int missing = 0;
        int fixed = 0;
        int skipped = 0;
        int failed = 0;
        for (Path path : imgs) {
            if (!Files.isRegularFile(path)) {
                failed++;
                System.out.println("MISS " + path);
                continue;
            }
            WzImageFile img = null;
            try {
                img = new WzImageFile(path.getFileName().toString(), path.toString(),
                        "GMS", WZ_GMS_IV, DEFAULT_KEY);
                if (!img.parse()) {
                    failed++;
                    System.out.println("PARSE_FAIL " + path);
                    continue;
                }
                WzImageProperty info = img.getChild("info");
                if (info == null) {
                    skipped++;
                    continue;
                }
                boolean changed = false;
                changed |= ensureCanvasOrigin(info, "icon", path);
                changed |= ensureCanvasOrigin(info, "iconRaw", path);
                if (!changed) {
                    skipped++;
                    continue;
                }
                missing++;
                if (dry) {
                    System.out.println("WOULD_FIX " + path);
                    continue;
                }
                if (!img.save(path)) {
                    failed++;
                    System.out.println("SAVE_FAIL " + path);
                    continue;
                }
                fixed++;
                System.out.println("FIXED " + path);
            } catch (Throwable t) {
                failed++;
                System.out.println("ERR " + path + " " + t.getClass().getSimpleName() + " " + t.getMessage());
            } finally {
                if (img != null) {
                    try {
                        img.unparse();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        System.out.printf("DONE dry=%s total=%d needOrigin=%d fixed=%d skipped=%d failed=%d%n",
                dry, imgs.size(), missing, fixed, skipped, failed);
    }

    private static boolean ensureCanvasOrigin(WzImageProperty info, String canvasName, Path path) {
        WzImageProperty child = info.getChild(canvasName);
        if (!(child instanceof WzCanvasProperty canvas)) {
            return false;
        }
        WzImageProperty origin = canvas.getChild("origin");
        if (origin instanceof WzVectorProperty) {
            return false;
        }
        int h = canvas.getHeight();
        if (h <= 0) {
            h = 32;
        }
        int y = h;
        WzVectorProperty vec = new WzVectorProperty("origin", 0, y, canvas, canvas.getWzImage());
        canvas.addChild(vec);
        if (canvas.getWzImage() != null) {
            canvas.getWzImage().setChanged(true);
        }
        System.out.println("  +" + canvasName + "/origin=(0," + y + ") " + path.getFileName());
        return true;
    }
}
