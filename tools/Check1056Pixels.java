import orange.wz.provider.*;
import orange.wz.provider.properties.*;

public class Check1056Pixels {
  static int nonZero(WzCanvasProperty c) {
    int[] argb = c.getARGBPixels();
    int n = 0;
    for (int p : argb) if ((p & 0xFF000000) != 0) n++;
    return n;
  }
  public static void main(String[] a) throws Exception {
    for (String path : new String[]{
        "F:\\MXD_dev\\BeiDou-Client\\Data\\Skill\\313.img",
        "F:\\MXD_dev\\BeiDou-Client\\Data\\Skill\\313.img.bak_pre_mr_report_20260824_112305"}) {
      WzImageFile f = new WzImageFile("313.img", path, "083-GMS", WzAESConstant.WZ_GMS_IV, WzAESConstant.DEFAULT_KEY);
      if (!f.parse()) { System.out.println(path + " FAIL"); continue; }
      WzCanvasProperty c = (WzCanvasProperty) f.getChild("skill").getChild("3131056").getChild("effect").getChild("0");
      System.out.println(path + " effect/0 nonZeroPx=" + nonZero(c) + " total=" + c.getWidth() * c.getHeight());
      f.unparse();
    }
  }
}
