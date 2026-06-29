package orange.wz;

import lombok.extern.slf4j.Slf4j;
import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.properties.WzCanvasProperty;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

// 抽样导出合成后的卡片 icon 为 PNG，用于人工核验合成效果
@Slf4j
public class MonsterCardIconVerify {
    public static void main(String[] args) throws Exception {
        String imgPath = args[0];
        String outDir = args[1];
        int sampleCount = args.length > 2 ? Integer.parseInt(args[2]) : 10;

        WzImageFile imgFile = new WzImageFile("0238.img", imgPath, "GMS", WZ_GMS_IV, DEFAULT_KEY);
        if (!imgFile.parse()) {
            log.error("解析失败: {}", imgPath);
            return;
        }

        new File(outDir).mkdirs();

        List<WzImageProperty> cards = imgFile.getChildren();
        int n = 0;
        for (WzImageProperty card : cards) {
            if (n >= sampleCount) break;
            WzImageProperty info = card.getChild("info");
            if (info == null) continue;
            WzImageProperty iconProp = info.getChild("icon");
            if (!(iconProp instanceof WzCanvasProperty icon)) continue;

            BufferedImage img = icon.getPngImage(true);
            File outFile = new File(outDir, card.getName() + ".png");
            ImageIO.write(img, "png", outFile);
            log.info("导出: {}", outFile.getAbsolutePath());
            n++;
        }
    }
}
