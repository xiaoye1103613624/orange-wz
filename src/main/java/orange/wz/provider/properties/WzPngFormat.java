package orange.wz.provider.properties;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public enum WzPngFormat {
    ARGB4444(1),
    ARGB8888(2),
    // Format3(3), // 废弃 实际是 format 1 + scale 2 其实和后面的 DXT3 1026是一样的
    ARGB1555(257),
    RGB565(513),
    // Format517(517), // 废弃 实际是 format 513 + scale 4
    DXT3(1026),
    DXT5(2050),
    BC7(4098);

    private final int value;

    WzPngFormat(int value) {
        this.value = value;
    }

    public static WzPngFormat getByValue(int value) {
        for (WzPngFormat e : values()) {
            if (e.getValue() == value) {
                return e;
            }
        }
        log.warn("未知的图片压缩格式 {}", value);
        throw new RuntimeException("未知的图片压缩格式 " + value);
    }

    /**
     * v083 {@code IWzCanvas} 原生像素格式：4444 / 8888 / 565 / DXT3。
     * ARGB1555、DXT5、BC7 是后期格式，打进 v083 会解不开。
     */
    public boolean isV083Native() {
        return this == ARGB4444 || this == ARGB8888 || this == RGB565 || this == DXT3;
    }

    /** 仅 ARGB4444、RGB565 支持 magLevel/scale；8888 等必须 scale=0。 */
    public boolean supportsScale() {
        return this == ARGB4444 || this == RGB565;
    }

    public int coerceScale(int scale) {
        return supportsScale() ? scale : 0;
    }
}
