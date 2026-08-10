package orange.wz;

import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.properties.WzCanvasProperty;
import orange.wz.provider.properties.WzPngFormat;
import orange.wz.provider.properties.WzVectorProperty;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * Replace UIWindow.img/Item/FullBackgrnd with double-height PNG (603x502)
 * for ExpandItem 96→192. Uses ARGB4444 + scale=0 (v083-safe).
 */
public class PatchFullBackgrnd {
    public static void main(String[] args) throws Exception {
        Path uiLive = Path.of(args[0]);
        Path pngPath = Path.of(args[1]);
        Path work = Path.of(args[2]);
        Files.createDirectories(work);

        Path src = work.resolve("UIWindow_src.img");
        Path out = work.resolve("UIWindow_fullbg.img");
        Files.copy(uiLive, src, StandardCopyOption.REPLACE_EXISTING);

        BufferedImage png = ImageIO.read(pngPath.toFile());
        if (png == null) {
            throw new IllegalStateException("cannot read png");
        }
        System.out.println("png=" + png.getWidth() + "x" + png.getHeight());

        WzImageFile img = new WzImageFile(src.getFileName().toString(), src.toString(),
                "GMS", WZ_GMS_IV, DEFAULT_KEY);
        if (!img.parse()) {
            throw new IllegalStateException("parse fail");
        }
        WzImageProperty item = img.getChild("Item");
        WzImageProperty fb = item != null ? item.getChild("FullBackgrnd") : null;
        if (!(fb instanceof WzCanvasProperty canvas)) {
            throw new IllegalStateException("FullBackgrnd missing");
        }
        System.out.println("before " + canvas.getWidth() + "x" + canvas.getHeight());
        canvas.setPng(png, WzPngFormat.ARGB4444, 0);
        WzImageProperty origin = canvas.getChild("origin");
        if (origin instanceof WzVectorProperty vec) {
            // keep existing origin if any
            System.out.println("origin " + vec.getX() + "," + vec.getY());
        }
        Files.deleteIfExists(out);
        Files.deleteIfExists(Path.of(out + ".bak"));
        if (!img.save(out)) {
            throw new IllegalStateException("save fail");
        }
        Path bak = Path.of(out + ".bak");
        if (Files.exists(bak) && (!Files.exists(out) || Files.size(out) < 1000)) {
            Files.move(bak, out, StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println("out size=" + Files.size(out));

        // verify
        WzImageFile check = new WzImageFile("chk.img", out.toString(), "GMS", WZ_GMS_IV, DEFAULT_KEY);
        check.parse();
        WzCanvasProperty c2 = (WzCanvasProperty) check.getChild("Item").getChild("FullBackgrnd");
        System.out.println("after " + c2.getWidth() + "x" + c2.getHeight() + " scale=" + c2.getScale());
        if (c2.getWidth() != 603 || c2.getHeight() != 502 || c2.getScale() != 0) {
            throw new IllegalStateException("verify fail");
        }

        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Files.copy(uiLive, uiLive.resolveSibling("UIWindow.img.bak_pre_fullbg_" + ts),
                StandardCopyOption.REPLACE_EXISTING);
        Files.copy(out, uiLive, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("deployed " + uiLive + " size=" + Files.size(uiLive));
    }
}
