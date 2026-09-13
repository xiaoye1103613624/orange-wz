import orange.wz.provider.*;
import orange.wz.provider.properties.*;
import java.nio.file.*;
import java.util.*;

/**
 * 2026-08-24 hyper archer fix:
 * - 1056/3231056: fresh MR deepClone ball+effect(30)+hit; strip effect0/1/2; MR hit (no hitAfter)
 * - 3131003/3231003: lower ball VFX by +35 origin Y on all ball canvases
 * - Sound/Skill.img: clone MR Use/Hit for hyper archer ids missing on live
 */
public class FixArcherHyper20260824 {
  static final String[] VIZ = {"effect", "hit", "ball", "affected", "effect0", "effect1", "effect2", "action"};

  static void stripViz(WzImageProperty sk) {
    for (String n : VIZ) if (sk.getChild(n) != null) sk.removeChild(n);
  }

  static void cloneChild(WzImageProperty srcParent, String name, WzImageProperty target) {
    WzImageProperty src = srcParent.getChild(name);
    if (src == null) throw new IllegalStateException("missing donor " + name + " for " + target.getName());
    WzImageProperty copy = src.deepClone(target);
    copy.setName(name);
    var img = target.getWzImage();
    copy.setWzImage(img);
    copy.setChildrenWzImage(img);
    target.addChild(copy);
    System.out.println(target.getName() + " +" + name);
  }

  static void lowerBallOriginY(WzImageProperty sk, int deltaY) {
    WzImageProperty ball = sk.getChild("ball");
    if (ball == null) return;
    int n = 0;
    for (WzImageProperty c : ball.getChildren()) {
      if (c instanceof WzCanvasProperty canv) {
        WzVectorProperty o = (WzVectorProperty) canv.getChild("origin");
        if (o != null) {
          o.setY(o.getY() + deltaY);
          n++;
        }
      }
    }
    System.out.println(sk.getName() + " ball origin Y +" + deltaY + " on " + n + " frames");
  }

  static void patchShoot1056(WzImageProperty sk, WzImageProperty mr) {
    stripViz(sk);
    cloneChild(mr, "ball", sk);
    cloneChild(mr, "effect", sk);
    cloneChild(mr, "hit", sk);
    WzImageProperty l1 = sk.getChild("level").getChild("1");
    if (l1 != null && l1.getChild("cooltime") != null) {
      l1.removeChild("cooltime");
      System.out.println(sk.getName() + " -cooltime");
    }
  }

  static void patchSoundBook(String book, Path liveSound, Path mrSound) throws Exception {
    WzImageFile live = new WzImageFile("Skill.img", liveSound.toString(), "083-GMS",
        WzAESConstant.WZ_GMS_IV, WzAESConstant.DEFAULT_KEY);
    WzImageFile mr = new WzImageFile("Skill.img", mrSound.toString(), "083-GMS",
        WzAESConstant.WZ_GMS_IV, WzAESConstant.DEFAULT_KEY);
    if (!live.parse() || !mr.parse()) throw new RuntimeException("sound parse fail");
  for (String id : new String[]{book + "1003", book + "1056", book + "1012", book + "1007"}) {
      if (live.getChild(id) != null) continue;
      WzImageProperty src = mr.getChild(id);
      if (src == null) {
        System.out.println("Sound skip missing MR " + id);
        continue;
      }
      WzImageProperty copy = src.deepClone(live);
      copy.setName(id);
      copy.setWzImage(live);
      copy.setChildrenWzImage(live);
      live.addChild(copy);
      System.out.println("Sound +" + id);
    }
    mr.unparse();
    live.setChanged(true);
    if (!live.save()) throw new RuntimeException("sound save fail");
    live.unparse();
  }

  static void patchSkillBook(String book, Path liveImg, Path mrImg, Path out) throws Exception {
    Path work = out.getParent().resolve(book + ".img.work");
    Files.copy(liveImg, work, StandardCopyOption.REPLACE_EXISTING);
    Path donorCopy = out.getParent().resolve(book + ".img.mr_donor");
    Files.copy(mrImg, donorCopy, StandardCopyOption.REPLACE_EXISTING);

    WzImageFile img = new WzImageFile(book + ".img", work.toString(), "083-GMS",
        WzAESConstant.WZ_GMS_IV, WzAESConstant.DEFAULT_KEY);
    WzImageFile donorImg = new WzImageFile(book + ".img", donorCopy.toString(), "083-GMS",
        WzAESConstant.WZ_GMS_IV, WzAESConstant.DEFAULT_KEY);
    if (!img.parse() || !donorImg.parse()) throw new RuntimeException("parse " + book);

    WzImageProperty root = img.getChild("skill");
    WzImageProperty mrRoot = donorImg.getChild("skill");

    patchShoot1056(root.getChild(book + "1056"), mrRoot.getChild(book + "1056"));
    lowerBallOriginY(root.getChild(book + "1003"), 35);

    donorImg.unparse();
    img.setChanged(true);
    if (!img.save()) throw new RuntimeException("save " + book);
    img.unparse();

    Path bak = Path.of(work + ".bak");
    Path saved = Files.exists(bak)
        && Files.getLastModifiedTime(bak).toMillis() >= Files.getLastModifiedTime(work).toMillis() - 5000
        ? bak : work;
    Files.copy(saved, out, StandardCopyOption.REPLACE_EXISTING);
    System.out.println("OUT " + out + " " + Files.size(out));
  }

  public static void main(String[] a) throws Exception {
    Path outDir = Path.of("F:\\MXD_dev\\_import_staging\\archer_hyper_fix_20260824");
    Files.createDirectories(outDir);
    Path liveSkill = Path.of("F:\\MXD_dev\\BeiDou-Client\\Data\\Skill");
    Path mrSkill = Path.of("F:\\MapleRoot Full Repack\\Game Files\\Data\\Skill");
    Path liveSound = Path.of("F:\\MXD_dev\\BeiDou-Client\\Data\\Sound\\Skill.img");
    Path mrSound = Path.of("F:\\MapleRoot Full Repack\\Game Files\\Data\\Sound\\Skill.img");

    patchSkillBook("313", liveSkill.resolve("313.img"), mrSkill.resolve("313.img"), outDir.resolve("313.img"));
    if (Files.exists(mrSkill.resolve("323.img"))) {
      patchSkillBook("323", liveSkill.resolve("323.img"), mrSkill.resolve("323.img"), outDir.resolve("323.img"));
    }
    if (Files.exists(liveSound) && Files.exists(mrSound)) {
      Path soundOut = outDir.resolve("Sound_Skill.img");
      Files.copy(liveSound, soundOut, StandardCopyOption.REPLACE_EXISTING);
      patchSoundBook("313", soundOut, mrSound);
      if (Files.exists(mrSkill.resolve("323.img"))) patchSoundBook("323", soundOut, mrSound);
    }
  }
}
