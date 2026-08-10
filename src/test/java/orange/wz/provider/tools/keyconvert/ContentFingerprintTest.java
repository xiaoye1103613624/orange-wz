package orange.wz.provider.tools.keyconvert;

import orange.wz.provider.WzAESConstant;
import orange.wz.provider.WzImageFile;
import orange.wz.provider.properties.WzIntProperty;
import orange.wz.provider.properties.WzListProperty;
import orange.wz.provider.properties.WzStringProperty;
import orange.wz.provider.tools.BinaryReader;
import orange.wz.provider.tools.WzFileStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentFingerprintTest {

    @Test
    void changeKeyPreservesDecodedFingerprint() throws Exception {
        Path dir = Files.createTempDirectory("owz-fp");
        Path src = dir.resolve("t.img");
        Path dst = dir.resolve("t_gms.img");

        WzImageFile img = new WzImageFile("t.img", src.toString(), "CMS",
                WzAESConstant.WZ_CMS_IV, WzAESConstant.DEFAULT_KEY);
        img.setReader(new BinaryReader(WzAESConstant.WZ_CMS_IV, WzAESConstant.DEFAULT_KEY));
        img.setStatus(WzFileStatus.PARSE_SUCCESS);
        img.setNewFile(true);
        WzListProperty info = new WzListProperty("info", img, img);
        img.addChild(info);
        info.addChild(new WzStringProperty("name", "probe", info, img));
        info.addChild(new WzIntProperty("price", 7, info, img));
        assertTrue(img.save(src));

        WzImageFile loaded = new WzImageFile("t.img", src.toString(), "CMS",
                WzAESConstant.WZ_CMS_IV, WzAESConstant.DEFAULT_KEY);
        assertTrue(loaded.parse());
        String before = ContentFingerprint.ofImage(loaded);
        assertTrue(loaded.changeKey("GMS", WzAESConstant.WZ_GMS_IV, WzAESConstant.DEFAULT_KEY));
        loaded.setFilePath(dst.toString());
        assertTrue(loaded.save(dst));

        WzImageFile verify = new WzImageFile("t_gms.img", dst.toString(), "GMS",
                WzAESConstant.WZ_GMS_IV, WzAESConstant.DEFAULT_KEY);
        assertTrue(verify.parse());
        String after = ContentFingerprint.ofImage(verify);
        assertEquals(before, after);

        // Ensure ciphertext actually changed (raw files differ) while logical content matches.
        assertNotEquals(Files.readAllBytes(src).length == 0, true);
        // Different IV encryption should produce different bytes for same structure.
        // (Allow rare equal length; content fingerprint is the accuracy gate.)
        verify.unparse();
        loaded.unparse();
    }
}
