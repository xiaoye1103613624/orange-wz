package orange.wz;

import lombok.extern.slf4j.Slf4j;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.properties.WzIntProperty;
import orange.wz.provider.properties.WzListProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * Clone S-grade potential scrolls 02049750~54 from 02049412 and fix A/S success/cursed rates.
 *
 * <pre>
 * mvnw -q -DskipTests compile exec:java ^
 *   -Dexec.mainClass=orange.wz.PotentialScrollClone095 ^
 *   -Dexec.args="&lt;0204.img&gt; [out.img]"
 * </pre>
 */
@Slf4j
public class PotentialScrollClone095 {

    private static final Map<String, int[]> RATES = new LinkedHashMap<>();
    private static final Map<String, String> CLONE = new LinkedHashMap<>();

    static {
        RATES.put("02049408", new int[]{100, 0});
        RATES.put("02049409", new int[]{15, 0});
        RATES.put("02049410", new int[]{30, 0});
        RATES.put("02049411", new int[]{50, 0});
        RATES.put("02049412", new int[]{80, 100});
        RATES.put("02049413", new int[]{50, 100});
        RATES.put("02049750", new int[]{80, 100});
        RATES.put("02049751", new int[]{50, 100});
        RATES.put("02049752", new int[]{30, 100});
        RATES.put("02049753", new int[]{15, 60});
        RATES.put("02049754", new int[]{10, 60});
        for (String id : new String[]{"02049750", "02049751", "02049752", "02049753", "02049754"}) {
            CLONE.put(id, "02049412");
        }
    }

    public static void main(String[] args) throws Exception {
        Path src = Path.of(args.length > 0
                ? args[0]
                : "E:/mxd_soft/2.客户端/083/beidou_client_xiaoye/BeiDou-Client_1/Data/Item/Consume/0204.img");
        Path out = Path.of(args.length > 1
                ? args[1]
                : "E:/pro/orange-wz/_tmp_pot_port/0204_clone095.img");
        Files.createDirectories(out.getParent());
        Path work = out.resolveSibling("0204_clone095_work.img");
        Files.copy(src, work, StandardCopyOption.REPLACE_EXISTING);

        WzImageFile img = open(work);

        for (Map.Entry<String, String> e : CLONE.entrySet()) {
            String nid = e.getKey();
            String donorId = e.getValue();
            if (img.getChild(nid) != null) {
                log.info("exists {}", nid);
                continue;
            }
            WzImageProperty donor = img.getChild(donorId);
            if (!(donor instanceof WzListProperty donorList)) {
                throw new IllegalStateException("missing donor " + donorId);
            }
            WzListProperty cloned = donorList.deepClone(img);
            cloned.setNameAnyway(nid);
            cloned.setWzImage(img);
            cloned.setChildrenWzImage(img);
            if (!img.addChild(cloned)) {
                throw new IllegalStateException("addChild failed for " + nid);
            }
            log.info("cloned {} <- {}", nid, donorId);
        }

        for (Map.Entry<String, int[]> e : RATES.entrySet()) {
            String id = e.getKey();
            int success = e.getValue()[0];
            int cursed = e.getValue()[1];
            WzImageProperty item = img.getChild(id);
            if (!(item instanceof WzListProperty list)) {
                log.warn("missing {}", id);
                continue;
            }
            WzImageProperty infoNode = list.getChild("info");
            if (!(infoNode instanceof WzListProperty info)) {
                log.warn("{} no info", id);
                continue;
            }
            setInt(info, "success", success);
            setInt(info, "cursed", cursed);
            log.info("rate {} success={} cursed={}", id, success, cursed);
        }

        Files.deleteIfExists(out);
        Files.deleteIfExists(Path.of(out + ".bak"));
        if (!img.save(out)) {
            throw new IllegalStateException("save failed: " + out);
        }
        Path bak = Path.of(out + ".bak");
        if (Files.exists(bak) && (!Files.exists(out) || Files.size(out) < 1000)) {
            Files.move(bak, out, StandardCopyOption.REPLACE_EXISTING);
        }
        long size = Files.size(out);
        log.info("out={} size={}", out, size);
        if (size < 180_000) {
            throw new IllegalStateException("output too small: " + size);
        }

        // deploy
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Files.copy(src, src.resolveSibling("0204.img.bak_clone095_" + ts), StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.copy(out, src, StandardCopyOption.REPLACE_EXISTING);
            log.info("deployed {}", src);
        } catch (Exception ex) {
            Path neu = Path.of(src + ".new");
            Files.copy(out, neu, StandardCopyOption.REPLACE_EXISTING);
            log.warn("live locked, wrote {}", neu);
        }
    }

    private static void setInt(WzListProperty info, String name, int value) {
        WzImageProperty p = info.getChild(name);
        if (p instanceof WzIntProperty ip) {
            ip.setValue(value);
        } else if (p == null) {
            info.addChild(new WzIntProperty(name, value, info, info.getWzImage()));
        } else {
            throw new IllegalStateException(name + " is not int: " + p.getClass());
        }
        if (info.getWzImage() != null) {
            info.getWzImage().setChanged(true);
        }
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
