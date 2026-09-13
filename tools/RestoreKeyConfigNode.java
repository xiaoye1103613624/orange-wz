package tools;

import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;
import static orange.wz.provider.WzAESConstant.*;

public class RestoreKeyConfigNode {
  public static void main(String[] a) throws Exception {
    if (a.length < 2) {
      System.out.println("Usage: RestoreKeyConfigNode <liveUIWindow.img> <srcUIWindow.img>");
      System.exit(2);
    }
    String dstPath = a[0], srcPath = a[1];
    WzImageFile src = new WzImageFile("src.img", srcPath, "083-GMS", WZ_GMS_IV, DEFAULT_KEY);
    WzImageFile dst = new WzImageFile("dst.img", dstPath, "083-GMS", WZ_GMS_IV, DEFAULT_KEY);
    if (!src.parse() || !dst.parse()) { System.out.println("ERROR:parse"); System.exit(9); }
    WzImageProperty s = src.getChild("KeyConfig");
    if (s == null) { System.out.println("ERROR:src no KeyConfig"); System.exit(10); }
    if (dst.getChild("KeyConfig") != null) dst.removeChild("KeyConfig");
    WzImageProperty clone = s.deepClone(dst);
    dst.addChild(clone);
    clone.setWzImage(dst);
    clone.setChildrenWzImage(dst);
    if (!dst.save(java.nio.file.Path.of(dstPath))) { System.out.println("ERROR:save"); System.exit(13); }
    System.out.println("RESTORED KeyConfig from " + srcPath + " -> " + dstPath);
    src.unparse(); dst.unparse();
  }
}
