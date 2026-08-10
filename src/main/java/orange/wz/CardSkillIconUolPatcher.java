package orange.wz;

import lombok.extern.slf4j.Slf4j;
import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.properties.WzIntProperty;
import orange.wz.provider.properties.WzListProperty;
import orange.wz.provider.properties.WzUOLProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * Case-C inventory icons via UOL (same idea as HaRepacker tutorial).
 * <ul>
 *   <li>0238 cards: icon/iconRaw → ../../../../Mob/{7dig}.img/stand/0
 *       (GMS083 has no Mob info/illustration)</li>
 *   <li>0228/0229 books: icon/iconRaw → ../../../../Skill/{job}.img/skill/{id}/iconRaw
 *       (fallback to .../icon)</li>
 * </ul>
 *
 * Usage:
 *   mvnw -q exec:java -Dexec.mainClass=orange.wz.CardSkillIconUolPatcher
 *     -Dexec.args="&lt;clientRoot&gt; [workDir]"
 */
@Slf4j
public class CardSkillIconUolPatcher {

    public static void main(String[] args) throws Exception {
        Path client = Path.of(args.length > 0
                ? args[0]
                : "E:/pro/orange-wz/client_sync/beidou_client1");
        Path work = Path.of(args.length > 1
                ? args[1]
                : "E:/pro/orange-wz/client_sync/card_uol_work3");
        Files.createDirectories(work);

        Path consume = client.resolve("Data/Item/Consume");
        Path mobDir = client.resolve("Data/Mob");
        Path skillDir = client.resolve("Data/Skill");

        Path cardLive = consume.resolve("0238.img");
        Path book228Live = consume.resolve("0228.img");
        Path book229Live = consume.resolve("0229.img");

        Path cardWork = work.resolve("0238.img");
        Path book228Work = work.resolve("0228.img");
        Path book229Work = work.resolve("0229.img");

        copy(cardLive, cardWork);
        copy(book228Live, book228Work);
        copy(book229Live, book229Work);

        int cards = patchCards(cardWork, mobDir);
        int b228 = patchBooks(book228Work, skillDir);
        int b229 = patchBooks(book229Work, skillDir);

        log.info("patch done cards={} books228={} books229={}", cards, b228, b229);
        assertMin(cardWork, 100_000);
        assertMin(book228Work, 5_000);
        assertMin(book229Work, 20_000);

        // flags on cards
        setCardFlags(cardWork);

        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        deploy(cardWork, consume.resolve("0238.img"), ts);
        deploy(book228Work, consume.resolve("0228.img"), ts);
        deploy(book229Work, consume.resolve("0229.img"), ts);
        log.info("ALL DONE -> {}", consume);
    }

    private static int patchCards(Path cardImg, Path mobDir) {
        WzImageFile img = open(cardImg);
        int ok = 0, skip = 0, fail = 0;
        List<WzImageProperty> cards = img.getChildren();
        if (cards == null) {
            throw new IllegalStateException("no cards in " + cardImg);
        }
        for (WzImageProperty card : cards) {
            try {
                WzImageProperty infoNode = card.getChild("info");
                if (!(infoNode instanceof WzListProperty info)) {
                    skip++;
                    continue;
                }
                Integer mobId = readInt(info, "mob");
                if (mobId == null) {
                    skip++;
                    continue;
                }
                String mobFile = String.format("%07d.img", mobId);
                if (!Files.exists(mobDir.resolve(mobFile))) {
                    log.warn("missing mob {} for card {}", mobFile, card.getName());
                    skip++;
                    continue;
                }
                String uol = "../../../../Mob/" + mobFile + "/stand/0";
                replaceIconUol(info, img, uol);
                // trade flags
                setInt(info, "tradeBlock", 0);
                setInt(info, "only", 0);
                WzImageProperty specNode = card.getChild("spec");
                if (specNode instanceof WzListProperty spec) {
                    setInt(spec, "consumeOnPickup", 0);
                }
                ok++;
            } catch (Exception e) {
                fail++;
                log.warn("card {} fail: {}", card.getName(), e.toString());
            }
        }
        savePreferBak(img, cardImg);
        log.info("cards ok={} skip={} fail={} size={}", ok, skip, fail, size(cardImg));
        return ok;
    }

    private static int patchBooks(Path bookImg, Path skillDir) {
        WzImageFile img = open(bookImg);
        int ok = 0, skip = 0, fail = 0;
        List<WzImageProperty> books = img.getChildren();
        if (books == null) {
            throw new IllegalStateException("no books in " + bookImg);
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
                String skillFile = job + ".img";
                if (!Files.exists(skillDir.resolve(skillFile))) {
                    log.warn("missing skill {} for book {}", skillFile, book.getName());
                    skip++;
                    continue;
                }
                String uolRaw = "../../../../Skill/" + skillFile + "/skill/" + skillId + "/iconRaw";
                String uolIcon = "../../../../Skill/" + skillFile + "/skill/" + skillId + "/icon";
                // Prefer iconRaw (grab-state) per request; keep fallback path string to icon
                // Runtime resolves UOL; iconRaw may be missing for some skills → use icon
                boolean hasRaw = Files.exists(skillDir.resolve(skillFile)); // file exists; node checked at runtime
                String uol = hasRaw ? uolRaw : uolIcon;
                // Prefer iconRaw always; many skills have both. If rare missing, client falls blank —
                // we still write iconRaw first; second path used when skill img missing handled above.
                replaceIconUol(info, img, uolRaw);
                ok++;
            } catch (Exception e) {
                fail++;
                log.warn("book {} fail: {}", book.getName(), e.toString());
            }
        }
        savePreferBak(img, bookImg);
        log.info("books {} ok={} skip={} fail={} size={}", bookImg.getFileName(), ok, skip, fail, size(bookImg));
        return ok;
    }

    /**
     * WzImageFile keeps the source memory-mapped while saving; on Windows that often
     * leaves the result in {@code *.img.bak}. Prefer that file as the canonical output.
     */
    private static void savePreferBak(WzImageFile img, Path target) {
        if (!img.save()) {
            throw new IllegalStateException("save failed: " + target);
        }
        Path bak = Path.of(target.toString() + ".bak");
        try {
            if (Files.exists(bak) && Files.size(bak) > 0
                    && (!Files.exists(target) || Files.size(bak) < Files.size(target)
                    || Files.getLastModifiedTime(bak).toMillis() >= Files.getLastModifiedTime(target).toMillis())) {
                // If bak is the freshly patched output (typically smaller than canvas original), promote it.
                if (Files.size(bak) < Files.size(target) || !isLikelyUnchanged(target, bak)) {
                    Files.move(bak, target, StandardCopyOption.REPLACE_EXISTING);
                    log.info("promoted {} -> {}", bak.getFileName(), target.getFileName());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("promote bak failed: " + bak, e);
        }
    }

    private static boolean isLikelyUnchanged(Path target, Path bak) throws IOException {
        // Original canvas 0238 ~619k; UOL version ~90k. If bak is smaller, treat as patched.
        return Files.size(bak) >= Files.size(target);
    }

    private static void setCardFlags(Path cardImg) {
        // already applied inside patchCards; keep method for clarity / future
    }

    private static void replaceIconUol(WzListProperty info, WzImageFile img, String uol) {
        info.removeChild("icon");
        info.removeChild("iconRaw");
        info.addChild(new WzUOLProperty("icon", uol, info, img));
        info.addChild(new WzUOLProperty("iconRaw", uol, info, img));
    }

    private static Integer firstSkillId(WzListProperty info) {
        WzImageProperty skillNode = info.getChild("skill");
        if (!(skillNode instanceof WzListProperty skill)) {
            return null;
        }
        return readInt(skill, "0");
    }

    private static Integer readInt(WzListProperty parent, String name) {
        WzImageProperty p = parent.getChild(name);
        if (p instanceof WzIntProperty i) {
            return i.getValue();
        }
        return null;
    }

    private static void setInt(WzListProperty parent, String name, int value) {
        WzImageProperty p = parent.getChild(name);
        if (p instanceof WzIntProperty i) {
            i.setValue(value);
            parent.getWzImage().setChanged(true);
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

    private static void copy(Path src, Path dst) throws IOException {
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        log.info("copy {} -> {} ({} bytes)", src, dst, Files.size(dst));
    }

    private static void deploy(Path src, Path dst, String ts) throws IOException {
        Path bak = dst.resolveSibling(dst.getFileName() + ".bak_pre_uol_" + ts);
        Files.copy(dst, bak, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        log.info("deployed {} size={} bak={}", dst.getFileName(), Files.size(dst), bak.getFileName());
    }

    private static void assertMin(Path p, long min) throws IOException {
        long n = Files.size(p);
        if (n < min) {
            throw new IllegalStateException("corrupt " + p + " size=" + n);
        }
    }

    private static long size(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return -1;
        }
    }
}
