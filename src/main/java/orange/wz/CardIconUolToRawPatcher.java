package orange.wz;

import lombok.extern.slf4j.Slf4j;
import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.properties.WzCanvasProperty;
import orange.wz.provider.properties.WzListProperty;
import orange.wz.provider.properties.WzUOLProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * Monster cards only: {@code info/icon} → UOL {@code iconRaw} (same node, no PNG re-encode).
 * Avoids client error 38 from Java zlib re-bake. Inventory shows raw mob art (no 0.8 scale).
 *
 * Does NOT touch 0228/0229.
 */
@Slf4j
public class CardIconUolToRawPatcher {

    public static void main(String[] args) throws Exception {
        Path client = Path.of(args.length > 0
                ? args[0]
                : "E:/pro/orange-wz/client_sync/beidou_client1");
        Path work = Path.of(args.length > 1
                ? args[1]
                : "E:/pro/orange-wz/client_sync/card_uol_raw_work");
        Files.createDirectories(work);

        Path live = client.resolve("Data/Item/Consume/0238.img");
        Path src = work.resolve("0238_src.img");
        Path out = work.resolve("0238_final.img");
        Files.copy(live, src, StandardCopyOption.REPLACE_EXISTING);

        WzImageFile img = open(src);
        int ok = 0, skip = 0;
        List<WzImageProperty> cards = img.getChildren();
        for (WzImageProperty card : cards) {
            WzImageProperty infoNode = card.getChild("info");
            if (!(infoNode instanceof WzListProperty info)) {
                skip++;
                continue;
            }
            if (!(info.getChild("iconRaw") instanceof WzCanvasProperty)) {
                log.warn("{} no iconRaw canvas, skip", card.getName());
                skip++;
                continue;
            }
            // Drop icon canvas (or prior UOL); point icon → sibling iconRaw
            info.removeChild("icon");
            info.addChild(new WzUOLProperty("icon", "iconRaw", info, img));
            ok++;
        }

        Files.deleteIfExists(out);
        Files.deleteIfExists(Path.of(out + ".bak"));
        if (!img.save(out)) {
            throw new IllegalStateException("save failed");
        }
        Path bak = Path.of(out + ".bak");
        if (Files.exists(bak) && (!Files.exists(out) || Files.size(out) < 1000)) {
            Files.move(bak, out, StandardCopyOption.REPLACE_EXISTING);
        }
        long size = Files.size(out);
        if (size < 50_000) {
            throw new IllegalStateException("output too small: " + size);
        }
        log.info("cards ok={} skip={} size={}", ok, skip, size);

        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path dst = live;
        Files.copy(dst, dst.resolveSibling("0238.img.bak_pre_uolraw_" + ts), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(out, dst, StandardCopyOption.REPLACE_EXISTING);
        log.info("deployed {} size={}", dst, Files.size(dst));
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
