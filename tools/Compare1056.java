import orange.wz.provider.*;
import orange.wz.provider.properties.*;

public class Compare1056 {
  static void one(String path) throws Exception {
    WzImageFile f = new WzImageFile("313.img", path, "083-GMS", WzAESConstant.WZ_GMS_IV, WzAESConstant.DEFAULT_KEY);
    f.parse();
    WzImageProperty sk = f.getChild("skill").getChild("3131056");
    WzImageProperty eff = sk.getChild("effect");
    int canv = 0;
    if (eff != null) for (WzImageProperty c : eff.getChildren()) if (c instanceof WzCanvasProperty) canv++;
    System.out.println(path + " effectFrames=" + canv + " effect0=" + (sk.getChild("effect0") != null)
        + " hitKids=" + (sk.getChild("hit").getChild("0").getChildren().size()));
    f.unparse();
  }
  public static void main(String[] a) throws Exception {
    one("F:\\MXD_dev\\BeiDou-Client\\Data\\Skill\\313.img");
    one("F:\\MXD_dev\\BeiDou-Client\\Data\\Skill\\313.img.bak_pre_mr_report_20260824_112305");
    one("F:\\MapleRoot Full Repack\\Game Files\\Data\\Skill\\313.img");
  }
}
