import orange.wz.provider.WzImageFile;
import static orange.wz.provider.WzAESConstant.*;
public class ListImgNodes {
  public static void main(String[] a) throws Exception {
    for (String p : a) {
      WzImageFile f = new WzImageFile(new java.io.File(p).getName(), p, "083-GMS", WZ_GMS_IV, DEFAULT_KEY);
      if (!f.parse()) { System.out.println(p + " PARSE_FAIL"); continue; }
      System.out.println("== " + p);
      for (var c : f.getChildren()) System.out.println("  " + c.getName());
      System.out.println("  DamageRank=" + (f.getChild("DamageRank") != null));
      f.unparse();
    }
  }
}