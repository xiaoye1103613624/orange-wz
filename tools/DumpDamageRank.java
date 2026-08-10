import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;
import static orange.wz.provider.WzAESConstant.*;

public class DumpDamageRank {
    static void dump(WzImageProperty p, int depth) {
        String ind = "  ".repeat(depth);
        System.out.println(ind + p.getName() + " (" + p.getPropertyType() + ")");
        if (depth < 3 && p.isListProperty()) {
            for (WzImageProperty c : p.getChildren()) {
                dump(c, depth + 1);
            }
        }
    }

    public static void main(String[] a) throws Exception {
        WzImageFile f = new WzImageFile("UIWindow.img", a[0], "083-GMS", WZ_GMS_IV, DEFAULT_KEY);
        f.parse();
        WzImageProperty dr = f.getChild("DamageRank");
        if (dr == null) {
            System.out.println("MISSING");
            return;
        }
        dump(dr, 0);
        f.unparse();
    }
}
