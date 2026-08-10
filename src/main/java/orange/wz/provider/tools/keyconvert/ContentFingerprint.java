package orange.wz.provider.tools.keyconvert;

import orange.wz.provider.WzDirectory;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.WzObject;
import orange.wz.provider.properties.WzCanvasProperty;
import orange.wz.provider.properties.WzDoubleProperty;
import orange.wz.provider.properties.WzFloatProperty;
import orange.wz.provider.properties.WzIntProperty;
import orange.wz.provider.properties.WzLongProperty;
import orange.wz.provider.properties.WzShortProperty;
import orange.wz.provider.properties.WzSoundProperty;
import orange.wz.provider.properties.WzStringProperty;
import orange.wz.provider.properties.WzUOLProperty;
import orange.wz.provider.properties.WzVectorProperty;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Key-independent structural fingerprint of decoded WZ content.
 * Used to prove changeKey / batch convert did not alter logical payload.
 */
public final class ContentFingerprint {
    private ContentFingerprint() {
    }

    public static String ofImage(WzImage image) {
        if (image == null) {
            throw new IllegalArgumentException("image is null");
        }
        if (!image.parse()) {
            throw new IllegalStateException("cannot fingerprint unparsable image: " + image.getName());
        }
        MessageDigest md = sha256();
        update(md, "IMG");
        // Do NOT hash image file name — save-as / output rename must not fail verify.
        walkProperties(md, image.getChildren());
        return HexFormat.of().formatHex(md.digest());
    }

    public static String ofDirectory(WzDirectory directory) {
        if (directory == null) {
            throw new IllegalArgumentException("directory is null");
        }
        MessageDigest md = sha256();
        update(md, "DIR");
        // Skip archive root display name; hash structure + image payloads only.
        walkDirectory(md, directory);
        return HexFormat.of().formatHex(md.digest());
    }

    private static void walkDirectory(MessageDigest md, WzDirectory directory) {
        for (WzObject child : directory.getChildren()) {
            if (child instanceof WzDirectory sub) {
                update(md, "D");
                update(md, sub.getName());
                walkDirectory(md, sub);
            } else if (child instanceof WzImage image) {
                update(md, "I");
                update(md, image.getName());
                update(md, ofImage(image));
            }
        }
    }

    private static void walkProperties(MessageDigest md, List<WzImageProperty> properties) {
        if (properties == null) {
            return;
        }
        for (WzImageProperty property : properties) {
            update(md, "P");
            update(md, property.getName());
            update(md, property.getType().name());
            digestPropertyValue(md, property);
            if (property.isListProperty()) {
                walkProperties(md, property.getChildren());
            }
        }
    }

    private static void digestPropertyValue(MessageDigest md, WzImageProperty property) {
        switch (property) {
            case WzStringProperty p -> update(md, p.getValue());
            case WzIntProperty p -> update(md, Integer.toString(p.getValue()));
            case WzShortProperty p -> update(md, Short.toString(p.getValue()));
            case WzLongProperty p -> update(md, Long.toString(p.getValue()));
            case WzFloatProperty p -> update(md, Float.toString(p.getValue()));
            case WzDoubleProperty p -> update(md, Double.toString(p.getValue()));
            case WzUOLProperty p -> update(md, p.getValue());
            case WzVectorProperty p -> {
                update(md, Integer.toString(p.getX()));
                update(md, Integer.toString(p.getY()));
            }
            case WzCanvasProperty canvas -> digestCanvas(md, canvas);
            case WzSoundProperty sound -> digestSound(md, sound);
            default -> {
                // list / null / convex / raw / lua: structure covered by children / type name
            }
        }
    }

    private static void digestCanvas(MessageDigest md, WzCanvasProperty canvas) {
        update(md, Integer.toString(canvas.getWidth()));
        update(md, Integer.toString(canvas.getHeight()));
        update(md, canvas.getFormat() == null ? "null" : canvas.getFormat().name());
        update(md, Integer.toString(canvas.getScale()));
        BufferedImage image = canvas.getPngImage(false);
        if (image == null) {
            update(md, "NOIMG");
            return;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        update(md, Integer.toString(w));
        update(md, Integer.toString(h));
        // Sample every pixel via getRGB — key-independent decoded content.
        long mix = 0x9E3779B97F4A7C15L;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                mix = Long.rotateLeft(mix ^ (image.getRGB(x, y) & 0xFFFFFFFFL), 13);
            }
        }
        update(md, Long.toHexString(mix));
    }

    private static void digestSound(MessageDigest md, WzSoundProperty sound) {
        update(md, Integer.toString(sound.getLenMs()));
        byte[] bytes = sound.getSoundBytes(false);
        if (bytes == null) {
            update(md, "NOSND");
            return;
        }
        update(md, Integer.toString(bytes.length));
        md.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void update(MessageDigest md, String value) {
        if (value == null) {
            md.update((byte) 0);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        md.update((byte) 1);
        md.update(intBytes(bytes.length));
        md.update(bytes);
    }

    private static byte[] intBytes(int value) {
        return new byte[]{
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
    }
}
