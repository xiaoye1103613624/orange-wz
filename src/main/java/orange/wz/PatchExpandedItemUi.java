package orange.wz;

import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.properties.WzCanvasProperty;
import orange.wz.provider.properties.WzListProperty;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * Patch Item/FullBackgrnd height 289 -> 502 for ExpandedItem.
 * Usage: java orange.wz.PatchExpandedItemUi <src.img> <dst.img> [fullBackgrnd.png]
 */
public final class PatchExpandedItemUi {
    private static final int TARGET_HEIGHT = 502;
    private static final int SLOT_TOP = 50;
    private static final int ROW_PITCH = 34;
    private static final int ROWS_FIRST_BLOCK = 6;

    public static void main(String[] args) throws Exception {
        if (args.length >= 1 && "--inspect".equals(args[0])) {
            inspect(args.length >= 2 ? Path.of(args[1]) : null);
            return;
        }
        if (args.length < 2) {
            System.err.println("Usage: PatchExpandedItemUi <src.img> <dst.img> [fullBackgrnd.png]");
            System.err.println("       PatchExpandedItemUi --inspect <UIWindow.img>");
            System.exit(1);
        }
        Path src = Path.of(args[0]);
        Path dst = Path.of(args[1]);
        Path pngOverride = args.length >= 3 ? Path.of(args[2]) : null;
        String name = src.getFileName().toString();

        WzImageFile img = new WzImageFile(name, src.toString(), "BeiDou", WZ_GMS_IV, DEFAULT_KEY);
        if (!img.parse()) {
            System.err.println("parse failed: " + src);
            System.exit(2);
        }

        WzListProperty item = findList(img, "Item");
        if (item == null) {
            System.err.println("Item node missing");
            System.exit(3);
        }
        WzImageProperty fullBgProp = item.getChild("FullBackgrnd");
        if (!(fullBgProp instanceof WzCanvasProperty)) {
            System.err.println("Item/FullBackgrnd canvas missing");
            System.exit(4);
        }
        WzCanvasProperty canvas = (WzCanvasProperty) fullBgProp;

        BufferedImage extended;
        if (pngOverride != null && Files.isRegularFile(pngOverride)) {
            extended = ImageIO.read(pngOverride.toFile());
            if (extended == null) {
                System.err.println("failed to read png: " + pngOverride);
                System.exit(6);
            }
            System.out.println("Using FullBackgrnd PNG: " + pngOverride + " " + extended.getWidth() + "x" + extended.getHeight());
        } else {
            extended = extendFullBackgrnd(canvas.getPngImage(false));
        }
        canvas.setPng(extended, canvas.getFormat(), canvas.getScale());
        img.setChanged(true);

        Files.createDirectories(dst.getParent());
        if (!img.save(dst)) {
            System.err.println("save failed: " + dst);
            System.exit(5);
        }
        img.unparse();
        System.out.println("OK: " + dst + " FullBackgrnd " + extended.getWidth() + "x" + extended.getHeight());
    }

    private static void inspect(Path path) throws Exception {
        if (path == null) {
            System.err.println("Usage: PatchExpandedItemUi --inspect <UIWindow.img>");
            System.exit(1);
        }
        WzImageFile img = new WzImageFile("UIWindow.img", path.toString(), "BeiDou", WZ_GMS_IV, DEFAULT_KEY);
        if (!img.parse()) {
            System.err.println("parse failed: " + path);
            System.exit(2);
        }
        WzListProperty item = findList(img, "Item");
        if (item == null) {
            System.err.println("Item node missing");
            System.exit(3);
        }
        for (String name : new String[]{"Backgrnd", "FullBackgrnd"}) {
            WzImageProperty child = item.getChild(name);
            if (child instanceof WzCanvasProperty canvas) {
                var png = canvas.getPngImage(false);
                System.out.println(name + ": " + png.getWidth() + "x" + png.getHeight());
            } else {
                System.out.println(name + ": " + (child == null ? "missing" : child.getClass().getSimpleName()));
            }
        }
        img.unparse();
    }

    private static WzListProperty findList(WzImage parent, String name) {
        WzImageProperty child = parent.getChild(name);
        return child instanceof WzListProperty list ? list : null;
    }

    static BufferedImage extendFullBackgrnd(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (h >= TARGET_HEIGHT) {
            return img;
        }

        BufferedImage out = new BufferedImage(w, TARGET_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(img, 0, 0, null);

        int rowY = SLOT_TOP + (ROWS_FIRST_BLOCK - 1) * ROW_PITCH;
        if (rowY + ROW_PITCH > h) {
            rowY = Math.max(SLOT_TOP, h - ROW_PITCH - 40);
        }
        BufferedImage rowTpl = img.getSubimage(0, rowY, w, Math.min(ROW_PITCH, h - rowY));

        int frameY = Math.max(SLOT_TOP, h - 55);
        BufferedImage frameStrip = img.getSubimage(0, frameY, w, h - frameY);

        int y = h;
        while (y + ROW_PITCH <= TARGET_HEIGHT - frameStrip.getHeight()) {
            g.drawImage(rowTpl, 0, y, null);
            y += ROW_PITCH;
        }
        while (y < TARGET_HEIGHT - frameStrip.getHeight()) {
            int sliceH = Math.min(ROW_PITCH, TARGET_HEIGHT - frameStrip.getHeight() - y);
            g.drawImage(rowTpl.getSubimage(0, 0, w, sliceH), 0, y, null);
            y += sliceH;
        }
        g.drawImage(frameStrip, 0, TARGET_HEIGHT - frameStrip.getHeight(), null);
        g.dispose();
        return out;
    }

    // quick visual sanity export when run with 3rd arg
    static {
        // no-op
    }
}
