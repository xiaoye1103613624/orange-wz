package orange.wz.provider.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WzPngFormatTest {
    @Test
    void v083NativeIncludes4444And8888() {
        assertTrue(WzPngFormat.ARGB4444.isV083Native());
        assertTrue(WzPngFormat.ARGB8888.isV083Native());
        assertTrue(WzPngFormat.RGB565.isV083Native());
        assertTrue(WzPngFormat.DXT3.isV083Native());
        assertFalse(WzPngFormat.ARGB1555.isV083Native());
        assertFalse(WzPngFormat.DXT5.isV083Native());
        assertFalse(WzPngFormat.BC7.isV083Native());
    }

    @Test
    void argb8888CoercesScaleToZero() {
        assertEquals(0, WzPngFormat.ARGB8888.coerceScale(2));
        assertEquals(0, WzPngFormat.DXT3.coerceScale(1));
        assertEquals(2, WzPngFormat.ARGB4444.coerceScale(2));
        assertEquals(1, WzPngFormat.RGB565.coerceScale(1));
    }
}
