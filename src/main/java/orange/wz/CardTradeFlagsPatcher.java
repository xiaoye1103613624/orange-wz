package orange.wz;

import lombok.extern.slf4j.Slf4j;
import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.properties.WzIntProperty;
import orange.wz.provider.properties.WzListProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * Monster cards only: set info/tradeBlock=0, info/only=0, and
 * info|spec/consumeOnPickup=0. Does not touch canvases / skill books.
 *
 * <pre>
 * mvnw -q -DskipTests compile exec:java ^
 *   -Dexec.mainClass=orange.wz.CardTradeFlagsPatcher ^
 *   -Dexec.args="&lt;clientRoot&gt; [workDir] [nodeploy]"
 * </pre>
 */
@Slf4j
public class CardTradeFlagsPatcher {

    public static void main(String[] args) throws Exception {
        Path client = Path.of(args.length > 0
                ? args[0]
                : "E:/pro/orange-wz/client_sync/beidou_client1");
        Path work = Path.of(args.length > 1
                ? args[1]
                : "E:/pro/orange-wz/client_sync/card_trade_flags_work");
        Files.createDirectories(work);

        Path live = client.resolve("Data/Item/Consume/0238.img");
        if (!Files.exists(live)) {
            throw new IllegalStateException("missing: " + live);
        }

        Path workSrc = work.resolve("0238_src.img");
        Path workFinal = work.resolve("0238_final.img");
        Files.copy(live, workSrc, StandardCopyOption.REPLACE_EXISTING);

        // Inspect before
        int[] before = inspect(open(workSrc));
        log.info("BEFORE cards={} tradeBlock0={} tradeBlock1={} only0={} only1={} cop0={} cop1={} missingTB={} missingOnly={}",
                before[0], before[1], before[2], before[3], before[4], before[5], before[6], before[7], before[8]);

        WzImageFile img = open(workSrc);
        int ok = 0, skip = 0, changed = 0;
        List<WzImageProperty> cards = img.getChildren();
        if (cards == null) {
            throw new IllegalStateException("no children");
        }
        for (WzImageProperty card : cards) {
            WzImageProperty infoNode = card.getChild("info");
            if (!(infoNode instanceof WzListProperty info)) {
                skip++;
                continue;
            }
            boolean c1 = setInt(info, "tradeBlock", 0);
            boolean c2 = setInt(info, "only", 0);
            boolean c3 = false;
            if (info.getChild("consumeOnPickup") != null) {
                c3 = setInt(info, "consumeOnPickup", 0);
            }
            WzImageProperty specNode = card.getChild("spec");
            if (specNode instanceof WzListProperty spec && spec.getChild("consumeOnPickup") != null) {
                c3 = setInt(spec, "consumeOnPickup", 0) || c3;
            }
            ok++;
            if (c1 || c2 || c3) {
                changed++;
            }
        }

        Files.deleteIfExists(workFinal);
        Files.deleteIfExists(Path.of(workFinal + ".bak"));
        if (!img.save(workFinal)) {
            throw new IllegalStateException("save failed");
        }
        Path bak = Path.of(workFinal + ".bak");
        if (Files.exists(bak) && (!Files.exists(workFinal) || Files.size(workFinal) < 1000)) {
            Files.move(bak, workFinal, StandardCopyOption.REPLACE_EXISTING);
        }
        long size = Files.size(workFinal);
        if (size < 100_000) {
            throw new IllegalStateException("output too small: " + size);
        }

        int[] after = inspect(open(workFinal));
        log.info("AFTER cards={} tradeBlock0={} tradeBlock1={} only0={} only1={} cop0={} cop1={} missingTB={} missingOnly={}",
                after[0], after[1], after[2], after[3], after[4], after[5], after[6], after[7], after[8]);
        log.info("patched ok={} skip={} changed={} size={}", ok, skip, changed, size);

        if (after[2] != 0 || after[4] != 0) {
            throw new IllegalStateException("still have tradeBlock=1 or only=1");
        }
        if (after[1] < 300 || after[3] < 300) {
            throw new IllegalStateException("too few zero flags");
        }

        boolean deploy = args.length < 3 || !"nodeploy".equalsIgnoreCase(args[2]);
        if (!deploy) {
            log.info("nodeploy: ready at {}", workFinal);
            return;
        }
        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path liveBak = live.resolveSibling("0238.img.bak_pre_tradeflags_" + ts);
        Files.copy(live, liveBak, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(workFinal, live, StandardCopyOption.REPLACE_EXISTING);
        log.info("backup {} size={}", liveBak, Files.size(liveBak));
        log.info("deployed {} size={}", live, Files.size(live));
    }

    /** returns [cards, tb0, tb1, only0, only1, cop0, cop1, missingTB, missingOnly] */
    private static int[] inspect(WzImageFile img) {
        int cards = 0, tb0 = 0, tb1 = 0, o0 = 0, o1 = 0, c0 = 0, c1 = 0, missTb = 0, missOnly = 0;
        for (WzImageProperty card : img.getChildren()) {
            WzImageProperty infoNode = card.getChild("info");
            if (!(infoNode instanceof WzListProperty info)) {
                continue;
            }
            cards++;
            Integer tb = readInt(info, "tradeBlock");
            Integer only = readInt(info, "only");
            if (tb == null) {
                missTb++;
            } else if (tb == 0) {
                tb0++;
            } else {
                tb1++;
            }
            if (only == null) {
                missOnly++;
            } else if (only == 0) {
                o0++;
            } else {
                o1++;
            }
            Integer cop = readInt(info, "consumeOnPickup");
            if (cop == null) {
                WzImageProperty specNode = card.getChild("spec");
                if (specNode instanceof WzListProperty spec) {
                    cop = readInt(spec, "consumeOnPickup");
                }
            }
            if (cop != null) {
                if (cop == 0) {
                    c0++;
                } else {
                    c1++;
                }
            }
        }
        return new int[]{cards, tb0, tb1, o0, o1, c0, c1, missTb, missOnly};
    }

    private static Integer readInt(WzListProperty parent, String name) {
        WzImageProperty p = parent.getChild(name);
        if (p instanceof WzIntProperty i) {
            return i.getValue();
        }
        return null;
    }

    /** @return true if value changed */
    private static boolean setInt(WzListProperty parent, String name, int value) {
        WzImageProperty p = parent.getChild(name);
        if (p instanceof WzIntProperty i) {
            if (i.getValue() == value) {
                return false;
            }
            i.setValue(value);
            if (parent.getWzImage() != null) {
                parent.getWzImage().setChanged(true);
            }
            return true;
        }
        return false;
    }

    private static WzImageFile open(Path path) {
        WzImageFile img = new WzImageFile(path.getFileName().toString(), path.toString(),
                "GMS", WZ_GMS_IV, DEFAULT_KEY);
        if (!img.parse()) {
            throw new IllegalStateException("parse failed: " + path);
        }
        return img;
    }
}
