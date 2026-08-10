package orange.wz;

import lombok.extern.slf4j.Slf4j;
import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.properties.WzCanvasProperty;
import orange.wz.provider.properties.WzIntProperty;
import orange.wz.provider.properties.WzListProperty;
import orange.wz.provider.properties.WzPngFormat;
import orange.wz.provider.properties.WzVectorProperty;
import orange.wz.provider.tools.ImgTool;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * Bake inventory icons (Case C — UOL across Mob/Skill does not resolve).
 * <ul>
 *   <li>0238 cards: icon = iconRaw scaled by 0.8 (centered on 32×32)</li>
 *   <li>0228/0229 books: icon (+iconRaw) = Skill/.../iconRaw PNG</li>
 * </ul>
 *
 * mvnw -q exec:java -Dexec.mainClass=orange.wz.CardSkillIconBakePatcher
 *   -Dexec.args="&lt;clientRoot&gt; [workDir]"
 */
@Slf4j
public class CardSkillIconBakePatcher {

    private static final double CARD_RAW_SCALE = 0.8;
    private static final int SLOT = 32;

    public static void main(String[] args) throws Exception {
        Path client = Path.of(args.length > 0
                ? args[0]
                : "E:/pro/orange-wz/client_sync/beidou_client1");
        Path work = Path.of(args.length > 1
                ? args[1]
                : "E:/pro/orange-wz/client_sync/card_bake_work");
        Files.createDirectories(work);

        Path consume = client.resolve("Data/Item/Consume");
        Path skillDir = client.resolve("Data/Skill");

        Path cardSrc = consume.resolve("0238.img");
        Path book228Src = consume.resolve("0228.img");
        Path book229Src = consume.resolve("0229.img");

        Path cardOut = work.resolve("0238_baked.img");
        Path book228Out = work.resolve("0228_baked.img");
        Path book229Out = work.resolve("0229_baked.img");

        Files.copy(cardSrc, cardOut, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(book228Src, book228Out, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(book229Src, book229Out, StandardCopyOption.REPLACE_EXISTING);

        // Save to *distinct* paths — Windows mmap keeps the opened src locked,
        // so in-place save() cannot replace the work copy.
        Path cardFinal = work.resolve("0238_final.img");
        Path book228Final = work.resolve("0228_final.img");
        Path book229Final = work.resolve("0229_final.img");

        int cards = patchCards(cardOut, cardFinal);
        int b228 = patchBooks(book228Out, skillDir, book228Final);
        int b229 = patchBooks(book229Out, skillDir, book229Final);
        log.info("bake done cards={} books228={} books229={}", cards, b228, b229);

        assertMin(cardFinal, 100_000);
        assertMin(book228Final, 5_000);
        assertMin(book229Final, 20_000);

        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        deploy(cardFinal, consume.resolve("0238.img"), ts);
        deploy(book228Final, consume.resolve("0228.img"), ts);
        deploy(book229Final, consume.resolve("0229.img"), ts);
        log.info("ALL DONE -> {}", consume);
    }

    private static int patchCards(Path src, Path out) throws IOException {
        WzImageFile img = open(src);
        int ok = 0, skip = 0;
        List<WzImageProperty> cards = img.getChildren();
        if (cards == null || cards.isEmpty()) {
            throw new IllegalStateException("no cards");
        }
        for (WzImageProperty card : cards) {
            WzImageProperty infoNode = card.getChild("info");
            if (!(infoNode instanceof WzListProperty info)) {
                skip++;
                continue;
            }
            WzCanvasProperty iconRaw = asCanvas(info.getChild("iconRaw"));
            WzCanvasProperty icon = asCanvas(info.getChild("icon"));
            if (iconRaw == null) {
                log.warn("card {} no iconRaw canvas, skip", card.getName());
                skip++;
                continue;
            }
            BufferedImage raw = iconRaw.getPngImage(true);
            if (raw == null) {
                skip++;
                continue;
            }
            BufferedImage scaled = scaleCentered(raw, CARD_RAW_SCALE, SLOT, SLOT);
            WzPngFormat fmt = icon != null ? icon.getFormat() : WzPngFormat.ARGB4444;
            if (fmt == null) {
                fmt = WzPngFormat.ARGB4444;
            }
            if (icon == null) {
                // should not happen on restored canvas packs; skip create for simplicity
                skip++;
                continue;
            }
            // WZ scale byte: 0 = 1:1 pixels; 1 = (1<<1)=2x downscale storage.
            // Writing scale=1 with width/height=32 causes client EOF (error 38).
            icon.setPng(scaled, fmt, 0);
            ensureOrigin(icon, 0, SLOT);
            setInt(info, "tradeBlock", 0);
            setInt(info, "only", 0);
            WzImageProperty specNode = card.getChild("spec");
            if (specNode instanceof WzListProperty spec) {
                setInt(spec, "consumeOnPickup", 0);
            }
            ok++;
        }
        saveTo(img, out);
        log.info("cards ok={} skip={} size={}", ok, skip, Files.size(out));
        return ok;
    }

    private static int patchBooks(Path src, Path skillDir, Path out) throws IOException {
        WzImageFile img = open(src);
        int ok = 0, skip = 0, fail = 0;
        Map<Integer, WzImageFile> skillCache = new HashMap<>();
        try {
            List<WzImageProperty> books = img.getChildren();
            if (books == null) {
                throw new IllegalStateException("no books");
            }
            for (WzImageProperty book : books) {
                try {
                    WzImageProperty infoNode = book.getChild("info");
                    if (!(infoNode instanceof WzListProperty info)) {
                        skip++;
                        continue;
                    }
                    Integer skillId = firstSkillId(info);
                    if (skillId == null) {
                        skip++;
                        continue;
                    }
                    int job = skillId / 10000;
                    if (job <= 0) {
                        log.warn("book {} skillId={} -> job {}, skip", book.getName(), skillId, job);
                        skip++;
                        continue;
                    }
                    Path skillPath = skillDir.resolve(job + ".img");
                    if (!Files.exists(skillPath)) {
                        log.warn("missing skill {} for {}", skillPath.getFileName(), book.getName());
                        skip++;
                        continue;
                    }
                    WzImageFile skillImg = skillCache.get(job);
                    if (skillImg == null) {
                        skillImg = open(skillPath);
                        skillCache.put(job, skillImg);
                    }
                    WzImageProperty skillRoot = skillImg.getChild("skill");
                    if (skillRoot == null) {
                        skip++;
                        continue;
                    }
                    WzImageProperty skillNode = skillRoot.getChild(String.valueOf(skillId));
                    if (skillNode == null) {
                        log.warn("skill node {} missing in {}", skillId, skillPath.getFileName());
                        skip++;
                        continue;
                    }
                    WzCanvasProperty skillRaw = asCanvas(skillNode.getChild("iconRaw"));
                    if (skillRaw == null) {
                        skillRaw = asCanvas(skillNode.getChild("icon"));
                    }
                    if (skillRaw == null) {
                        skip++;
                        continue;
                    }
                    BufferedImage png = skillRaw.getPngImage(true);
                    if (png == null) {
                        skip++;
                        continue;
                    }
                    WzPngFormat fmt = skillRaw.getFormat() != null ? skillRaw.getFormat() : WzPngFormat.ARGB4444;
                    WzCanvasProperty bookIcon = asCanvas(info.getChild("icon"));
                    WzCanvasProperty bookRaw = asCanvas(info.getChild("iconRaw"));
                    if (bookIcon == null) {
                        skip++;
                        continue;
                    }
                    bookIcon.setPng(png, fmt, 0);
                    ensureOrigin(bookIcon, -2, SLOT);
                    if (bookRaw != null) {
                        bookRaw.setPng(png, fmt, 0);
                        ensureOrigin(bookRaw, -2, SLOT);
                    }
                    ok++;
                } catch (Exception e) {
                    fail++;
                    log.warn("book {} fail: {}", book.getName(), e.toString());
                }
            }
        } finally {
            for (WzImageFile s : skillCache.values()) {
                try {
                    s.clear();
                } catch (Exception ignored) {
                }
            }
        }
        saveTo(img, out);
        log.info("books {} ok={} skip={} fail={} size={}", out.getFileName(), ok, skip, fail, Files.size(out));
        return ok;
    }

    /** iconRaw * scale, centered on transparent slotW×slotH canvas. */
    private static BufferedImage scaleCentered(BufferedImage src, double scale, int slotW, int slotH) {
        BufferedImage scaled = ImgTool.scale(src, scale);
        // Fit into inventory slot if oversized after 0.8 scale
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

    private static Integer firstSkillId(WzListProperty info) {
        WzImageProperty skillNode = info.getChild("skill");
        if (!(skillNode instanceof WzListProperty skill)) {
            return null;
        }
        WzImageProperty p = skill.getChild("0");
        if (p instanceof WzIntProperty i) {
            return i.getValue();
        }
        return null;
    }

    private static WzCanvasProperty asCanvas(WzImageProperty p) {
        return p instanceof WzCanvasProperty c ? c : null;
    }

    private static void setInt(WzListProperty parent, String name, int value) {
        WzImageProperty p = parent.getChild(name);
        if (p instanceof WzIntProperty i) {
            i.setValue(value);
            if (parent.getWzImage() != null) {
                parent.getWzImage().setChanged(true);
            }
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

    /**
     * Save to a path that is NOT the memory-mapped source (Windows lock).
     * If replace fails, promote {@code target.bak}.
     */
    private static void saveTo(WzImageFile img, Path target) throws IOException {
        Files.deleteIfExists(target);
        Files.deleteIfExists(Path.of(target + ".bak"));
        if (!img.save(target)) {
            throw new IllegalStateException("save failed: " + target);
        }
        Path bak = Path.of(target.toString() + ".bak");
        if ((!Files.exists(target) || Files.size(target) < 1000)
                && Files.exists(bak) && Files.size(bak) > 1000) {
            Files.move(bak, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("promoted {} -> {}", bak.getFileName(), target.getFileName());
        }
        if (!Files.exists(target) || Files.size(target) < 1000) {
            throw new IllegalStateException("corrupt after save: " + target
                    + " size=" + (Files.exists(target) ? Files.size(target) : -1));
        }
    }

    private static void deploy(Path src, Path dst, String ts) throws IOException {
        Path bak = dst.resolveSibling(dst.getFileName() + ".bak_pre_bake_" + ts);
        Files.copy(dst, bak, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        log.info("deployed {} size={} (bak {})", dst.getFileName(), Files.size(dst), bak.getFileName());
    }

    private static void assertMin(Path p, long min) throws IOException {
        long n = Files.size(p);
        if (n < min) {
            throw new IllegalStateException("corrupt " + p + " size=" + n);
        }
    }
}
