package orange.wz;

import lombok.extern.slf4j.Slf4j;
import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.properties.WzCanvasProperty;
import orange.wz.provider.properties.WzListProperty;
import orange.wz.provider.properties.WzPngFormat;
import orange.wz.provider.properties.WzVectorProperty;
import orange.wz.provider.tools.ImgTool;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * Monster cards only: bake {@code info/icon} = {@code iconRaw} × 0.8, centered on 32×32,
 * ARGB4444, WZ scale byte = 0 (1:1). Does not touch 0228/0229.
 *
 * <pre>
 * mvnw -q -DskipTests compile exec:java ^
 *   -Dexec.mainClass=orange.wz.CardIconBake08Patcher ^
 *   -Dexec.args="&lt;clientRoot&gt; [workDir] [optionalOriginal0238]"
 * </pre>
 */
@Slf4j
public class CardIconBake08Patcher {

    private static final double RAW_SCALE = 0.8;
    private static final int SLOT = 32;

    public static void main(String[] args) throws Exception {
        Path client = Path.of(args.length > 0
                ? args[0]
                : "E:/pro/orange-wz/client_sync/beidou_client1");
        Path work = Path.of(args.length > 1
                ? args[1]
                : "E:/pro/orange-wz/client_sync/card_bake08_work");
        Files.createDirectories(work);

        Path live = client.resolve("Data/Item/Consume/0238.img");
        Path source = args.length > 2 ? Path.of(args[2]) : live;
        if (!Files.exists(source)) {
            throw new IllegalStateException("missing source: " + source);
        }

        Path workSrc = work.resolve("0238_src.img");
        Path workFinal = work.resolve("0238_final.img");
        Files.copy(source, workSrc, StandardCopyOption.REPLACE_EXISTING);

        WzImageFile img = open(workSrc);
        int ok = 0, skip = 0;
        for (WzImageProperty card : img.getChildren()) {
            WzImageProperty infoNode = card.getChild("info");
            if (!(infoNode instanceof WzListProperty info)) {
                skip++;
                continue;
            }
            WzCanvasProperty iconRaw = asCanvas(info.getChild("iconRaw"));
            if (iconRaw == null) {
                log.warn("{} no iconRaw canvas, skip", card.getName());
                skip++;
                continue;
            }
            BufferedImage raw = iconRaw.getPngImage(true);
            if (raw == null) {
                skip++;
                continue;
            }
            BufferedImage scaled = scaleCentered(raw, RAW_SCALE, SLOT, SLOT);

            WzCanvasProperty icon = asCanvas(info.getChild("icon"));
            if (icon == null) {
                // Current live may be UOL → drop and recreate canvas child is complex;
                // require canvas source (pre-UOL bak).
                log.warn("{} icon is not canvas (UOL?), skip — use original bak as source", card.getName());
                skip++;
                continue;
            }
            icon.setPng(scaled, WzPngFormat.ARGB4444, 0);
            ensureOrigin(icon, 0, SLOT);
            ok++;
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
        log.info("cards ok={} skip={} size={}", ok, skip, size);
        if (ok < 300) {
            throw new IllegalStateException("too few cards baked: " + ok);
        }

        boolean deploy = args.length < 4 || !"nodeploy".equalsIgnoreCase(args[3]);
        if (!deploy) {
            log.info("nodeploy: ready at {}", workFinal);
            return;
        }
        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Files.copy(live, live.resolveSibling("0238.img.bak_pre_bake08_" + ts), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(workFinal, live, StandardCopyOption.REPLACE_EXISTING);
        log.info("deployed {} size={}", live, Files.size(live));
    }

    private static BufferedImage scaleCentered(BufferedImage src, double scale, int slotW, int slotH) {
        BufferedImage scaled = ImgTool.scale(src, scale);
        if (scaled.getWidth() > slotW || scaled.getHeight() > slotH) {
            double fit = Math.min((double) slotW / scaled.getWidth(), (double) slotH / scaled.getHeight());
            scaled = ImgTool.scale(scaled, fit);
        }
        BufferedImage out = new BufferedImage(slotW, slotH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int ox = (slotW - scaled.getWidth()) / 2;
        int oy = (slotH - scaled.getHeight()) / 2;
        g.drawImage(scaled, ox, oy, null);
        g.dispose();
        return out;
    }

    private static void ensureOrigin(WzCanvasProperty canvas, int x, int y) {
        WzImageProperty origin = canvas.getChild("origin");
        if (origin instanceof WzVectorProperty vec) {
            vec.setX(x);
            vec.setY(y);
            if (canvas.getWzImage() != null) {
                canvas.getWzImage().setChanged(true);
            }
        }
    }

    private static WzCanvasProperty asCanvas(WzImageProperty p) {
        return p instanceof WzCanvasProperty c ? c : null;
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
