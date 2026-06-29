package orange.wz;

import lombok.extern.slf4j.Slf4j;
import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.properties.WzCanvasProperty;
import orange.wz.provider.properties.WzPngFormat;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * 怪物卡片图标合成器
 * <p>
 * 把客户端 0238.img 中每张卡片的 iconRaw（怪物原图）
 * 合成到通用卡片框架上，生成独特的 icon，再写回 .img 文件。
 * <p>
 * 合成逻辑：
 *  ① 取第一张卡片的 icon 作为"卡片框架"底图（32×32）
 *  ② 将当前卡片的 iconRaw 按比例缩放到内容区（约24×24）
 *  ③ 居中贴到框架上 → 新 icon
 *
 * 用法：
 *   java -cp orange-wz.jar orange.wz.MonsterCardIconMaker [imgPath] [outputPath]
 *
 * 默认：
 *   imgPath    = E:\mxd_soft\2.客户端\083\BeiDou-ClientV16.1\BeiDou-Client\data\Item\Consume\0238.img
 *   outputPath = 同路径（原地覆盖）；如传不同路径则输出到新位置
 */
@Slf4j
public class MonsterCardIconMaker {

    // 客户端 0238.img 默认路径
    private static final String DEFAULT_IMG =
            "E:\\mxd_soft\\2.客户端\\083\\BeiDou-ClientV16.1\\BeiDou-Client\\data\\Item\\Consume\\0238.img";

    // icon 尺寸（GMS v83 使用 32×32）
    private static final int ICON_W = 32;
    private static final int ICON_H = 32;

    // 怪物内容区：在 icon 中央留 3px 边距（兼容卡片边框）
    private static final int PADDING = 3;
    private static final int CONTENT_W = ICON_W - PADDING * 2;  // 26
    private static final int CONTENT_H = ICON_H - PADDING * 2;  // 26

    public static void main(String[] args) {
        String imgPath = args.length > 0 ? args[0] : DEFAULT_IMG;
        String outPath = args.length > 1 ? args[1] : imgPath;

        log.info("读取 img: {}", imgPath);
        WzImageFile imgFile = new WzImageFile("0238.img", imgPath, "GMS", WZ_GMS_IV, DEFAULT_KEY);
        if (!imgFile.parse()) {
            log.error("解析失败: {}", imgPath);
            return;
        }

        // ---- 第一遍：拿到卡片框架底图（取第一张卡的 icon）----
        BufferedImage cardFrame = null;
        WzPngFormat targetFormat = WzPngFormat.ARGB4444; // v83 常见格式

        List<WzImageProperty> cards = imgFile.getChildren();
        if (cards == null || cards.isEmpty()) {
            log.error("0238.img 无子节点");
            return;
        }

        for (WzImageProperty card : cards) {
            WzCanvasProperty icon = getCanvas(card, "icon");
            if (icon != null) {
                cardFrame = icon.getPngImage(true);
                targetFormat = icon.getFormat();
                log.info("卡片框架底图来源: {}，尺寸: {}×{}, 格式: {}",
                        card.getName(), cardFrame.getWidth(), cardFrame.getHeight(), targetFormat);
                break;
            }
        }

        if (cardFrame == null) {
            log.error("未找到任何 icon 节点，无法获取卡片框架");
            return;
        }

        // ---- 第二遍：逐张卡片合成 ----
        int success = 0;
        int skip = 0;

        for (WzImageProperty card : cards) {
            WzCanvasProperty icon    = getCanvas(card, "icon");
            WzCanvasProperty iconRaw = getCanvas(card, "iconRaw");

            if (icon == null || iconRaw == null) {
                log.warn("卡片 {} 缺少 icon 或 iconRaw，跳过", card.getName());
                skip++;
                continue;
            }

            BufferedImage mobSprite = iconRaw.getPngImage(true);
            if (mobSprite == null) {
                log.warn("卡片 {} iconRaw 图片为 null，跳过", card.getName());
                skip++;
                continue;
            }

            // 合成
            BufferedImage composite = compose(cardFrame, mobSprite);

            // 写回 icon（保持原格式和 scale=1）
            icon.setPng(composite, targetFormat, 1);
            success++;
        }

        log.info("合成完成：成功 {}，跳过 {}", success, skip);

        // ---- 保存 ----
        imgFile.setFilePath(outPath);
        if (imgFile.save()) {
            log.info("已保存到: {}", outPath);
        } else {
            log.error("保存失败: {}", outPath);
        }
    }

    /**
     * 将怪物图片合成到卡片框架上
     *
     * @param cardFrame 32×32 卡片框架底图（保留边框）
     * @param mobSprite 怪物原始图（iconRaw）
     * @return 合成后的 32×32 图像
     */
    private static BufferedImage compose(BufferedImage cardFrame, BufferedImage mobSprite) {
        // 1. 创建空白画布（32×32 ARGB）
        BufferedImage result = new BufferedImage(ICON_W, ICON_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 2. 先画卡片框架（带边框、背景色）
        g.drawImage(cardFrame, 0, 0, ICON_W, ICON_H, null);

        // 3. 按比例缩放怪物图到内容区（26×26），保持纵横比
        int srcW = mobSprite.getWidth();
        int srcH = mobSprite.getHeight();
        double scale = Math.min((double) CONTENT_W / srcW, (double) CONTENT_H / srcH);
        int dstW = Math.max(1, (int) (srcW * scale));
        int dstH = Math.max(1, (int) (srcH * scale));

        // 4. 居中偏移
        int offsetX = PADDING + (CONTENT_W - dstW) / 2;
        int offsetY = PADDING + (CONTENT_H - dstH) / 2;

        // 5. 将怪物图贴到框架上（OVER 混合，透明部分保留框架）
        g.drawImage(mobSprite, offsetX, offsetY, dstW, dstH, null);

        g.dispose();
        return result;
    }

    /**
     * 从卡片节点中取 info/xxx 的 canvas
     */
    private static WzCanvasProperty getCanvas(WzImageProperty card, String name) {
        WzImageProperty info = card.getChild("info");
        if (info == null) return null;
        WzImageProperty prop = info.getChild(name);
        if (prop instanceof WzCanvasProperty canvas) return canvas;
        return null;
    }
}
