package orange.wz;

import orange.wz.model.Pair;
import orange.wz.provider.WzFile;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

public class MergeDamageRankFromWz {
    private static final String[] TARGET_IMGS = {"UIWindow.img", "UIWindow2.img"};

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("ERROR:args");
            System.exit(1);
        }
        Path srcWz = Path.of(args[0]);
        Path uiDir = Path.of(args[1]).resolve("UI");
        WzFile wz = new WzFile(srcWz.toString(), (short) -1, "083-GMS", WZ_GMS_IV, DEFAULT_KEY);
        if (!wz.parse()) {
            System.out.println("ERROR:wz parse " + wz.getStatus());
            System.exit(4);
        }
        Path tempDir = Files.createTempDirectory("damage-rank-wz-");
        List<Pair<WzImage, Path>> collector = new ArrayList<>();
        wz.exportFileToImg(tempDir, collector);
        for (Pair<WzImage, Path> pair : collector) {
            WzImage image = pair.getLeft();
            if (!image.parse()) {
                System.out.println("ERROR:export parse " + image.getName());
                System.exit(6);
            }
            if (!image.save(pair.getRight())) {
                System.out.println("ERROR:export save " + pair.getRight());
                System.exit(7);
            }
        }
        System.out.println("exported=" + collector.size() + " temp=" + tempDir);
        int mergedTotal = 0;
        for (String imgName : TARGET_IMGS) {
            Path srcImg = findExportedImg(tempDir, imgName);
            Path dstImg = uiDir.resolve(imgName);
            if (srcImg == null || !Files.isRegularFile(dstImg)) {
                System.out.println("[SKIP] " + imgName);
                continue;
            }
            Files.copy(dstImg, dstImg.resolveSibling(imgName + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            String result = mergeOne(srcImg, dstImg);
            System.out.println(imgName + " => " + result);
            if (result.startsWith("MERGED:")) {
                mergedTotal += Integer.parseInt(result.substring(7));
            } else if (result.startsWith("ERROR:")) {
                Files.copy(dstImg.resolveSibling(imgName + ".bak"), dstImg, StandardCopyOption.REPLACE_EXISTING);
                System.exit(5);
            }
        }
        verify(uiDir.resolve("UIWindow.img"));
        verify(uiDir.resolve("UIWindow2.img"));
        System.out.println("done added=" + mergedTotal);
        wz.clear();
    }

    private static Path findExportedImg(Path root, String imgName) throws Exception {
        try (var stream = Files.walk(root)) {
            return stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().equalsIgnoreCase(imgName)).findFirst().orElse(null);
        }
    }

    private static String mergeOne(Path srcFile, Path dstFile) {
        String name = srcFile.getFileName().toString();
        WzImageFile srcImg = new WzImageFile(name, srcFile.toString(), "083-GMS", WZ_GMS_IV, DEFAULT_KEY);
        WzImageFile dstImg = new WzImageFile(name, dstFile.toString(), "083-GMS", WZ_GMS_IV, DEFAULT_KEY);
        try {
            if (!srcImg.parse()) return "ERROR:src";
            if (!dstImg.parse()) return "ERROR:dst";
            WzImageProperty srcDamageRank = srcImg.getChild("DamageRank");
            if (srcDamageRank != null && dstImg.getChild("DamageRank") != null) {
                dstImg.removeChild("DamageRank");
                WzImageProperty clone = srcDamageRank.deepClone(dstImg);
                if (dstImg.addChild(clone)) {
                    clone.setWzImage(dstImg);
                    clone.setChildrenWzImage(dstImg);
                    return dstImg.save(dstFile) ? "REPLACED:" + srcDamageRank.getChildren().size() : "ERROR:save";
                }
                return "ERROR:replace";
            }
            int added = mergeTopLevel(srcImg, dstImg);
            if (added > 0) return dstImg.save(dstFile) ? "MERGED:" + added : "ERROR:save";
            return dstImg.getChild("DamageRank") != null ? "SKIPPED_SAME" : "SKIPPED_SAME";
        } catch (Throwable e) {
            return "ERROR:" + e;
        } finally {
            srcImg.unparse();
            dstImg.unparse();
        }
    }

    private static int mergeTopLevel(WzImage srcImg, WzImage dstImg) {
        int added = 0;
        for (WzImageProperty srcChild : srcImg.getChildren()) {
            WzImageProperty dstChild = dstImg.getChild(srcChild.getName());
            if (dstChild == null) {
                WzImageProperty clone = srcChild.deepClone(dstImg);
                if (dstImg.addChild(clone)) {
                    clone.setWzImage(dstImg);
                    clone.setChildrenWzImage(dstImg);
                    added++;
                }
            } else if (srcChild.isListProperty() && dstChild.isListProperty()) {
                added += mergeProperty(srcChild, dstChild, dstImg);
            }
        }
        return added;
    }

    private static int mergeProperty(WzImageProperty src, WzImageProperty dst, WzImage dstWzImage) {
        int added = 0;
        for (WzImageProperty srcChild : src.getChildren()) {
            WzImageProperty dstChild = dst.getChild(srcChild.getName());
            if (dstChild == null) {
                WzImageProperty clone = srcChild.deepClone(dst);
                if (dst.addChild(clone)) {
                    clone.setWzImage(dstWzImage);
                    clone.setChildrenWzImage(dstWzImage);
                    added++;
                }
            } else if (srcChild.isListProperty() && dstChild.isListProperty()) {
                added += mergeProperty(srcChild, dstChild, dstWzImage);
            }
        }
        return added;
    }

    private static void verify(Path imgPath) {
        if (!Files.isRegularFile(imgPath)) return;
        String name = imgPath.getFileName().toString();
        WzImageFile img = new WzImageFile(name, imgPath.toString(), "083-GMS", WZ_GMS_IV, DEFAULT_KEY);
        try {
            if (!img.parse()) {
                System.out.println("[VERIFY] " + name + " parse fail");
                return;
            }
            WzImageProperty dr = img.getChild("DamageRank");
            System.out.println("[VERIFY] " + name + " DamageRank=" + (dr != null ? "OK" : "MISSING"));
            if (dr == null) return;
            String[] required = {
                    "backgrndmax", "backgrndmin", "backgrndcenter", "backgrndbottom",
                    "title1", "title2", "gauge", "BtReset", "BtSwitch", "BtAuto",
                    "iconCommonAtk", "iconUnknownSkill"
            };
            for (String node : required) {
                System.out.println("[VERIFY]   " + node + "=" + (dr.getChild(node) != null ? "OK" : "MISSING"));
            }
            for (WzImageProperty child : dr.getChildren()) {
                System.out.println("[NODE] " + child.getName());
            }
        } finally {
            img.unparse();
        }
    }
}