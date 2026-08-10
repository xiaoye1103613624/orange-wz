package orange.wz.mcp.support;

import orange.wz.provider.WzDirectory;
import orange.wz.provider.WzFile;
import orange.wz.provider.WzFolder;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.WzObject;
import orange.wz.provider.properties.WzCanvasProperty;
import orange.wz.provider.tools.WzFileStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Releases parsed WZ/IMG/PNG references so the JVM GC can reclaim heap after bulk MCP ops.
 * unload_* previously only dropped list references without calling provider clear/unparse.
 */
public final class McpMemoryRelease {
    private McpMemoryRelease() {
    }

    /** Fully dispose a root (or any) object graph so GC can reclaim readers + PNG buffers. */
    public static void dispose(WzObject obj) {
        if (obj == null) {
            return;
        }
        if (obj instanceof WzDirectory dir) {
            if (dir.isWzFile()) {
                WzFile wzFile = dir.getWzFile();
                if (wzFile != null) {
                    wzFile.clear();
                } else {
                    dir.clear();
                }
            } else {
                dir.clear();
            }
            return;
        }
        if (obj instanceof WzImage image) {
            image.clear();
            return;
        }
        if (obj instanceof WzFolder folder) {
            List<WzObject> children = new ArrayList<>(folder.getChildren());
            for (WzObject child : children) {
                dispose(child);
                folder.remove(child);
            }
            return;
        }
        if (obj instanceof WzImageProperty prop) {
            prop.clear();
        }
    }

    /**
     * Soft-clear decoded PNG canvases and unparse unchanged images under loaded roots.
     * Keeps root membership; safe between copy/paste/save loops when source WZ must stay loaded.
     */
    public static void clearImageCaches(WzObject root) {
        if (root == null) {
            return;
        }
        if (root instanceof WzFolder folder) {
            for (WzObject child : folder.getChildren()) {
                clearImageCaches(child);
            }
            return;
        }
        if (root instanceof WzDirectory dir) {
            for (WzDirectory sub : dir.getDirectories()) {
                clearImageCaches(sub);
            }
            for (WzImage image : dir.getImages()) {
                softReleaseImage(image);
            }
            return;
        }
        if (root instanceof WzImage image) {
            softReleaseImage(image);
        }
    }

    private static void softReleaseImage(WzImage image) {
        if (image == null) {
            return;
        }
        // Unchanged imgs can be fully unparsed (lazy re-parse on next access).
        if (!image.isChanged() && image.getStatus() == WzFileStatus.PARSE_SUCCESS) {
            clearDecodedCanvases(image);
            image.unparse();
            return;
        }
        // Changed / newly created imgs: drop decoded BufferedImage only, keep compressed bytes for save.
        clearDecodedCanvases(image);
    }

    private static void clearDecodedCanvases(WzImage image) {
        if (image.getStatus() != WzFileStatus.PARSE_SUCCESS && !image.isChanged()) {
            return;
        }
        for (WzImageProperty prop : image.getChildren()) {
            clearDecodedCanvases(prop);
        }
    }

    private static void clearDecodedCanvases(WzImageProperty prop) {
        if (prop instanceof WzCanvasProperty canvas) {
            canvas.clearImage();
        }
        if (prop.isListProperty()) {
            for (WzImageProperty child : prop.getChildren()) {
                clearDecodedCanvases(child);
            }
        }
    }

    public static void hintGc() {
        System.gc();
    }
}
