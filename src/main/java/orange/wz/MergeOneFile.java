package orange.wz;

import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * 处理单个 .img 文件的节点合并，供 BatchMergeNodes 以子进程方式调用(隔离风险文件，
 * 例如 Sound 目录下部分音频文件解析头信息时可能触发 JDK 音频库底层原生崩溃，
 * 子进程崩溃不会影响主批处理进程)。
 * 用法：java -cp xxx orange.wz.MergeOneFile <源文件绝对路径> <目标文件绝对路径>
 * 结果通过标准输出最后一行返回：COPIED / MERGED:<n> / SKIPPED_SAME / ERROR:<message>
 */
public class MergeOneFile {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("ERROR:参数不足");
            System.exit(1);
        }
        Path srcFile = Path.of(args[0]);
        Path dstFile = Path.of(args[1]);

        try {
            if (!Files.exists(dstFile)) {
                Files.createDirectories(dstFile.getParent());
                Files.copy(srcFile, dstFile, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("COPIED");
                return;
            }

            String name = srcFile.getFileName().toString();
            WzImageFile srcImg = new WzImageFile(name, srcFile.toString(), "083-GMS", WZ_GMS_IV, DEFAULT_KEY);
            WzImageFile dstImg = new WzImageFile(name, dstFile.toString(), "083-GMS", WZ_GMS_IV, DEFAULT_KEY);
            try {
                if (!srcImg.parse()) {
                    System.out.println("ERROR:源文件解析失败，跳过比较");
                    return;
                }
                if (!dstImg.parse()) {
                    System.out.println("ERROR:目标文件解析失败，跳过比较");
                    return;
                }

                int added = mergeTopLevel(srcImg, dstImg);
                if (added > 0) {
                    boolean ok = dstImg.save(dstFile);
                    if (ok) {
                        System.out.println("MERGED:" + added);
                    } else {
                        System.out.println("ERROR:补充了" + added + "个节点但保存失败");
                    }
                } else {
                    System.out.println("SKIPPED_SAME");
                }
            } finally {
                srcImg.unparse();
                dstImg.unparse();
            }
        } catch (Throwable e) {
            System.out.println("ERROR:处理异常: " + e);
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
}
