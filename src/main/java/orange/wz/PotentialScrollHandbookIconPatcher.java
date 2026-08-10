package orange.wz;

import lombok.extern.slf4j.Slf4j;
import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.properties.WzCanvasProperty;
import orange.wz.provider.properties.WzListProperty;
import orange.wz.provider.properties.WzPngFormat;
import orange.wz.provider.properties.WzVectorProperty;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * Inject maplestory.io GMS250 (handbook) icons into Consume/0204.img for
 * 020493xx / 020494xx potential/hyper scrolls. Uses library setPng(ARGB4444, scale=0).
 * MCP set_png currently NPEs on save, so this library path is required.
 *
 * <pre>
 * mvnw -q -DskipTests compile exec:java ^
 *   -Dexec.mainClass=orange.wz.PotentialScrollHandbookIconPatcher ^
 *   -Dexec.args="&lt;0204.img&gt; &lt;iconsDir&gt; &lt;out.img&gt;"
 * </pre>
 */
@Slf4j
public class PotentialScrollHandbookIconPatcher {

    private static final List<String> IDS = List.of(
            "02049300", "02049301", "02049302", "02049303", "02049304", "02049305",
            "02049400", "02049401", "02049402", "02049404", "02049406", "02049407",
            "02049408", "02049409", "02049410", "02049411", "02049412", "02049413",
            "02049414", "02049415", "02049416", "02049419"
    );

    /** origin from 095 XML (approx). */
    private static final Map<String, int[]> ORIGIN = Map.ofEntries(
            Map.entry("02049300", new int[]{-1, 27}),
            Map.entry("02049301", new int[]{-1, 28}),
            Map.entry("02049302", new int[]{-1, 27}),
            Map.entry("02049303", new int[]{-1, 28}),
            Map.entry("02049304", new int[]{-1, 28}),
            Map.entry("02049305", new int[]{-1, 28}),
            Map.entry("02049400", new int[]{0, 31}),
            Map.entry("02049401", new int[]{0, 31}),
            Map.entry("02049402", new int[]{1, 29}),
            Map.entry("02049404", new int[]{-1, 28}),
            Map.entry("02049406", new int[]{1, 29}),
            Map.entry("02049407", new int[]{0, 31}),
            Map.entry("02049408", new int[]{0, 31}),
            Map.entry("02049409", new int[]{0, 31}),
            Map.entry("02049410", new int[]{0, 31}),
            Map.entry("02049411", new int[]{0, 31}),
            Map.entry("02049412", new int[]{0, 31}),
            Map.entry("02049413", new int[]{0, 31}),
            Map.entry("02049414", new int[]{1, 29}),
            Map.entry("02049415", new int[]{1, 29}),
            Map.entry("02049416", new int[]{0, 31}),
            Map.entry("02049419", new int[]{0, 31})
    );

    public static void main(String[] args) throws Exception {
        Path src = Path.of(args.length > 0
                ? args[0]
                : "E:/mxd_soft/2.客户端/083/beidou_client_xiaoye/BeiDou-Client_1/Data/Item/Consume/0204.img");
        Path icons = Path.of(args.length > 1
                ? args[1]
                : "E:/pro/BeiDou-Server_xy/gms-server/tools/_wz_stage_potential/icons");
        Path out = Path.of(args.length > 2
                ? args[2]
                : "E:/pro/BeiDou-Server_xy/gms-server/tools/_wz_stage_potential/0204_handbook_out.img");

        if (!Files.exists(src)) {
            throw new IllegalStateException("missing src: " + src);
        }
        if (!Files.isDirectory(icons)) {
            throw new IllegalStateException("missing icons dir: " + icons);
        }

        Path work = out.resolveSibling("0204_handbook_lib_work.img");
        Files.copy(src, work, StandardCopyOption.REPLACE_EXISTING);

        WzImageFile img = open(work);
        int ok = 0;
        for (String id : IDS) {
            WzImageProperty node = img.getChild(id);
            if (!(node instanceof WzListProperty item)) {
                log.warn("missing node {}", id);
                continue;
            }
            WzImageProperty infoNode = item.getChild("info");
            if (!(infoNode instanceof WzListProperty info)) {
                log.warn("{} no info", id);
                continue;
            }
            int numeric = Integer.parseInt(id);
            BufferedImage iconPng = readPng(icons.resolve(numeric + "_icon.png"));
            BufferedImage rawPng = readPng(icons.resolve(numeric + "_iconRaw.png"));
            WzCanvasProperty icon = asCanvas(info.getChild("icon"));
            WzCanvasProperty iconRaw = asCanvas(info.getChild("iconRaw"));
            if (icon == null || iconRaw == null) {
                log.warn("{} icon/iconRaw not canvas", id);
                continue;
            }
            icon.setPng(iconPng, WzPngFormat.ARGB4444, 0);
            iconRaw.setPng(rawPng, WzPngFormat.ARGB4444, 0);
            int[] origin = ORIGIN.getOrDefault(id, new int[]{-1, 27});
            ensureOrigin(icon, origin[0], origin[1]);
            ensureOrigin(iconRaw, origin[0], origin[1]);
            ok++;
            log.info("patched {} icon={}x{} raw={}x{}", id,
                    iconPng.getWidth(), iconPng.getHeight(),
                    rawPng.getWidth(), rawPng.getHeight());
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
        log.info("ok={} out={} size={}", ok, out, size);
        if (ok < IDS.size()) {
            throw new IllegalStateException("patched only " + ok + "/" + IDS.size());
        }
        if (size < 180_000) {
            throw new IllegalStateException("output too small: " + size);
        }

        boolean deploy = args.length < 4 || !"nodeploy".equalsIgnoreCase(args[3]);
        if (!deploy) {
            log.info("nodeploy: ready at {}", out);
            return;
        }
        Path live = src;
        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Files.copy(live, live.resolveSibling("0204.img.bak_pre_handbook_" + ts), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(out, live, StandardCopyOption.REPLACE_EXISTING);
        log.info("deployed {} size={}", live, Files.size(live));
    }

    private static BufferedImage readPng(Path p) throws Exception {
        if (!Files.exists(p)) {
            throw new IllegalStateException("missing png: " + p);
        }
        BufferedImage img = ImageIO.read(p.toFile());
        if (img == null) {
            throw new IllegalStateException("bad png: " + p);
        }
        return img;
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
