package orange.wz;

import lombok.extern.slf4j.Slf4j;
import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.properties.WzCanvasProperty;
import orange.wz.provider.properties.WzIntProperty;
import orange.wz.provider.properties.WzListProperty;
import orange.wz.provider.properties.WzPngFormat;
import orange.wz.provider.properties.WzVectorProperty;

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
 * Skill books only (0228/0229): bake {@code info/icon} and {@code info/iconRaw}
 * from Skill/{job}.img/skill/{id}/iconRaw (fallback icon), ARGB4444, WZ scale=0.
 * Does NOT touch 0238 cards.
 *
 * <pre>
 * mvnw -q -DskipTests compile exec:java ^
 *   -Dexec.mainClass=orange.wz.SkillBookIconBakePatcher ^
 *   -Dexec.args="&lt;clientRoot&gt; [workDir]"
 * </pre>
 */
@Slf4j
public class SkillBookIconBakePatcher {

    private static final int SLOT = 32;
    private static final int ORIGIN_X = -2;
    private static final int ORIGIN_Y = SLOT;

    public static void main(String[] args) throws Exception {
        Path client = Path.of(args.length > 0
                ? args[0]
                : "E:/pro/orange-wz/client_sync/beidou_client1");
        Path work = Path.of(args.length > 1
                ? args[1]
                : "E:/pro/orange-wz/client_sync/skillbook_bake_work");
        Files.createDirectories(work);

        Path consume = client.resolve("Data/Item/Consume");
        Path skillDir = client.resolve("Data/Skill");

        Path book228Src = consume.resolve("0228.img");
        Path book229Src = consume.resolve("0229.img");
        if (!Files.exists(book228Src) || !Files.exists(book229Src)) {
            throw new IllegalStateException("missing 0228/0229 under " + consume);
        }

        Path book228Work = work.resolve("0228_src.img");
        Path book229Work = work.resolve("0229_src.img");
        Path book228Final = work.resolve("0228_final.img");
        Path book229Final = work.resolve("0229_final.img");

        Files.copy(book228Src, book228Work, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(book229Src, book229Work, StandardCopyOption.REPLACE_EXISTING);

        int b228 = patchBooks(book228Work, skillDir, book228Final);
        int b229 = patchBooks(book229Work, skillDir, book229Final);
        log.info("bake done books228={} books229={}", b228, b229);

        assertMin(book228Final, 5_000);
        assertMin(book229Final, 20_000);

        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        boolean deployed228 = tryDeploy(book228Final, consume.resolve("0228.img"), ts);
        boolean deployed229 = tryDeploy(book229Final, consume.resolve("0229.img"), ts);
        if (!deployed228 || !deployed229) {
            log.warn("DEPLOY_PENDING workDir={} 0228={} 0229={} (close client and copy finals)",
                    work, book228Final, book229Final);
            System.out.println("DEPLOY_PENDING");
            System.out.println("0228_final=" + book228Final.toAbsolutePath()
                    + " size=" + Files.size(book228Final));
            System.out.println("0229_final=" + book229Final.toAbsolutePath()
                    + " size=" + Files.size(book229Final));
        } else {
            System.out.println("DEPLOYED");
            System.out.println("0228 size=" + Files.size(consume.resolve("0228.img")));
            System.out.println("0229 size=" + Files.size(consume.resolve("0229.img")));
        }
        System.out.println("ok228=" + b228 + " ok229=" + b229);
        log.info("ALL DONE books-only (0238 untouched)");
    }

    static int patchBooks(Path src, Path skillDir, Path out) throws IOException {
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
                    // v083 inventory: prefer ARGB4444 (never inject ARGB8888 web-style)
                    WzPngFormat fmt = WzPngFormat.ARGB4444;
                    WzCanvasProperty bookIcon = asCanvas(info.getChild("icon"));
                    WzCanvasProperty bookRaw = asCanvas(info.getChild("iconRaw"));
                    if (bookIcon == null) {
                        skip++;
                        continue;
                    }
                    // WZ scale byte: 0 = 1:1 pixels (scale=1 caused client error 38 EOF on cards)
                    bookIcon.setPng(png, fmt, 0);
                    ensureOrigin(bookIcon, ORIGIN_X, ORIGIN_Y);
                    if (bookRaw != null) {
                        bookRaw.setPng(png, fmt, 0);
                        ensureOrigin(bookRaw, ORIGIN_X, ORIGIN_Y);
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
        try {
            img.clear();
        } catch (Exception ignored) {
        }
        log.info("books {} ok={} skip={} fail={} size={}", out.getFileName(), ok, skip, fail, Files.size(out));
        System.out.println(out.getFileName() + " ok=" + ok + " skip=" + skip + " fail=" + fail
                + " size=" + Files.size(out));
        return ok;
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

    private static WzImageFile open(Path path) {
        WzImageFile img = new WzImageFile(path.getFileName().toString(), path.toString(),
                "GMS", WZ_GMS_IV, DEFAULT_KEY);
        if (!img.parse()) {
            throw new IllegalStateException("parse failed: " + path);
        }
        return img;
    }

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

    /** Backup then overwrite; returns false if destination locked. */
    private static boolean tryDeploy(Path src, Path dst, String ts) {
        try {
            Path bak = dst.resolveSibling(dst.getFileName() + ".bak_pre_bake_" + ts);
            Files.copy(dst, bak, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            log.info("deployed {} size={} (bak {})", dst.getFileName(), Files.size(dst), bak.getFileName());
            return true;
        } catch (IOException e) {
            log.warn("deploy failed for {}: {}", dst.getFileName(), e.toString());
            return false;
        }
    }

    private static void assertMin(Path p, long min) throws IOException {
        long n = Files.size(p);
        if (n < min) {
            throw new IllegalStateException("corrupt " + p + " size=" + n);
        }
    }
}
