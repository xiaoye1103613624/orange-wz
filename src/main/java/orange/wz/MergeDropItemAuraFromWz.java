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

/**
 * Merge dropItemAura + dropItemEffect nodes from a reference Effect.wz into client BasicEff.img.
 * Usage: java -cp ... orange.wz.MergeDropItemAuraFromWz <refEffect.wz> <clientBasicEff.img>
 */
public class MergeDropItemAuraFromWz {
    private static final String[] TARGET_NODES = {"dropItemAura", "dropItemEffect"};

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("ERROR:args <refEffect.wz> <clientBasicEff.img>");
            System.exit(1);
        }
        Path srcWz = Path.of(args[0]);
        Path workDir = Path.of("e:/pro/BeiDou-Server_xy/gms-server/tools/_drop_effect_work");
        Files.createDirectories(workDir);
        Path dstImg = Path.of(args[1]);
        Path mergedTmp = workDir.resolve("BasicEff.img.merged_dropaura");
        if (!Files.isRegularFile(srcWz) || !Files.isRegularFile(dstImg)) {
            System.out.println("ERROR:missing file");
            System.exit(2);
        }

        WzFile wz = new WzFile(srcWz.toString(), (short) -1, "083-GMS", WZ_GMS_IV, DEFAULT_KEY);
        if (!wz.parse()) {
            System.out.println("ERROR:wz parse " + wz.getStatus());
            System.exit(4);
        }

        Path tempDir = Files.createTempDirectory("drop-item-aura-wz-");
        List<Pair<WzImage, Path>> collector = new ArrayList<>();
        wz.exportFileToImg(tempDir, collector);
        for (Pair<WzImage, Path> pair : collector) {
            WzImage image = pair.getLeft();
            if (!image.parse() || !image.save(pair.getRight())) {
                System.out.println("ERROR:export " + image.getName());
                System.exit(6);
            }
        }
        wz.clear();

        Path srcImg = findExportedImg(tempDir, "BasicEff.img");
        if (srcImg == null) {
            System.out.println("ERROR:BasicEff.img not found in ref wz");
            System.exit(7);
        }

        Path backup = dstImg.resolveSibling("BasicEff.img.bak_dropaura");
        if (Files.isRegularFile(dstImg)) {
            Files.copy(dstImg, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        String result = mergeBasicEff(srcImg, dstImg, mergedTmp);
        System.out.println("merge => " + result);
        if (result.startsWith("ERROR:")) {
            Files.deleteIfExists(mergedTmp);
            System.exit(5);
        }
        verify(mergedTmp);
        try {
            Files.copy(mergedTmp, dstImg, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("deployed=" + dstImg);
        } catch (Exception locked) {
            Path deploy = dstImg.resolveSibling("BasicEff.img.dropaura_pending");
            Files.copy(mergedTmp, deploy, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("WARN:target locked, wrote " + deploy);
            System.out.println("WARN:close client and replace BasicEff.img manually");
        }
        System.out.println("done backup=" + backup + " merged=" + mergedTmp);
    }

    private static Path findExportedImg(Path root, String imgName) throws Exception {
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().equalsIgnoreCase(imgName))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static String mergeBasicEff(Path refFile, Path clientFile, Path outFile) {
        String name = clientFile.getFileName().toString();
        WzImageFile srcImg = new WzImageFile(name, refFile.toString(), "083-GMS", WZ_GMS_IV, DEFAULT_KEY);
        WzImageFile dstImg = new WzImageFile(name, clientFile.toString(), "083-GMS", WZ_GMS_IV, DEFAULT_KEY);
        try {
            if (!srcImg.parse()) {
                return "ERROR:src parse";
            }
            if (!dstImg.parse()) {
                return "ERROR:dst parse";
            }

            int merged = 0;
            for (String nodeName : TARGET_NODES) {
                WzImageProperty srcNode = srcImg.getChild(nodeName);
                if (srcNode == null) {
                    System.out.println("[SKIP] src missing " + nodeName);
                    continue;
                }
                WzImageProperty existing = dstImg.getChild(nodeName);
                if (existing != null) {
                    dstImg.removeChild(nodeName);
                }
                WzImageProperty clone = srcNode.deepClone(dstImg);
                if (!dstImg.addChild(clone)) {
                    return "ERROR:add " + nodeName;
                }
                clone.setWzImage(dstImg);
                clone.setChildrenWzImage(dstImg);
                merged += countNodes(srcNode);
                List<WzImageProperty> srcChildren = srcNode.getChildren();
                System.out.println("[MERGE] " + nodeName + " children="
                        + (srcChildren != null ? srcChildren.size() : 0));
            }

            if (merged == 0) {
                return "ERROR:no nodes merged";
            }
            return dstImg.save(outFile) ? "MERGED:" + merged : "ERROR:save";
        } catch (Throwable e) {
            return "ERROR:" + e;
        } finally {
            srcImg.unparse();
            dstImg.unparse();
        }
    }

    private static int countNodes(WzImageProperty node) {
        if (node == null) {
            return 0;
        }
        int count = 1;
        List<WzImageProperty> children = node.getChildren();
        if (children == null) {
            return count;
        }
        for (WzImageProperty child : children) {
            count += countNodes(child);
        }
        return count;
    }

    private static void verify(Path imgPath) {
        String name = imgPath.getFileName().toString();
        WzImageFile img = new WzImageFile(name, imgPath.toString(), "083-GMS", WZ_GMS_IV, DEFAULT_KEY);
        try {
            if (!img.parse()) {
                System.out.println("[VERIFY] parse fail");
                return;
            }
            for (String nodeName : TARGET_NODES) {
                WzImageProperty node = img.getChild(nodeName);
                System.out.println("[VERIFY] " + nodeName + "=" + (node != null ? "OK" : "MISSING"));
                if (node == null || !nodeName.equals("dropItemAura")) {
                    continue;
                }
                for (String grade : new String[] {"Rare", "Epic", "Unique", "Legendary", "Mythic"}) {
                    WzImageProperty gradeNode = node.getChild(grade);
                    System.out.println("[VERIFY]   " + grade + "=" + (gradeNode != null ? "OK" : "MISSING"));
                }
            }
        } finally {
            img.unparse();
        }
    }
}
