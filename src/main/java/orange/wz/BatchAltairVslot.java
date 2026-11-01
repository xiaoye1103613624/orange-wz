package orange.wz;

import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.properties.WzStringProperty;

import java.nio.file.Files;
import java.nio.file.Path;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * Altair Cap pure appearance mask: islot=Cp + full-body vslot (Wp/Sr/...).
 * Does not touch inventory UI — only Cap WZ drawing masks.
 * Usage: {@code java ... orange.wz.BatchAltairVslot <CapDir>}
 */
public class BatchAltairVslot {
    private static final String NEW_VSLOT =
            "CpH1H2H3H4H5H6HfHbHxHsAfAyAsAeMaPnSoGlGwSrSiWpWoWc";
    private static final String NEW_ISLOT = "Cp";

    private static final int[] IDS = {
            1008956, 1008955, 1008953, 1008950, 1008948, 1008946, 1008960, 1008942, 1008940, 1008939,
            1008933, 1008959, 1008927, 1008926, 1008923, 1008918, 1008913, 1008910, 1008901, 1008900,
            1008906, 1008929, 1009911, 1009912, 1009913, 1009914, 1009915, 1009916, 1009917, 1009918,
            1009919, 1009920, 1009921, 1009923, 1009930, 1009943, 1009922, 1009924, 1009927, 1009937,
            1009938, 1009939, 1009928, 1009929, 1009931, 1009932, 1009925, 1009926, 1009934, 1009933,
            1009935, 1009936, 1009940, 1009941, 1009942, 1009944, 1009945, 1009946, 1009947, 1009948,
            1009949, 1009950, 1009951, 1009952, 1009953, 1009954, 1009955, 1009956, 1009957, 1009958,
            1009959, 1009960, 1009961, 1009962, 1009963, 1009964, 1009965, 1009966, 1009967, 1009968,
            1009969, 1009970, 1009971, 1009972, 1009973, 1009974, 1009975, 1009976, 1009977, 1009978,
            1009979, 1009980, 1009981, 1009982, 1009983, 1009984, 1009985, 1009986, 1009987, 1009988,
            1009989, 1009990, 1009991, 1009992, 1009993, 1009994, 1009995, 1009996, 1009997, 1009998,
            1009999,
    };

    public static void main(String[] args) {
        Path dir = Path.of(args.length > 0 ? args[0] : "E:/MXD/BeiDou-Client_S9/Data/Character/Cap");
        int ok = 0;
        int fail = 0;
        int skip = 0;
        for (int id : IDS) {
            String name = String.format("0%d.img", id);
            Path img = dir.resolve(name);
            if (!Files.isRegularFile(img)) {
                System.out.println("MISS " + name);
                fail++;
                continue;
            }
            WzImageFile imgFile = new WzImageFile(name, img.toString(), "GMS", WZ_GMS_IV, DEFAULT_KEY);
            try {
                if (!imgFile.parse()) {
                    System.out.println("FAIL parse " + name);
                    fail++;
                    continue;
                }
                WzImageProperty info = imgFile.getChild("info");
                if (info == null) {
                    System.out.println("FAIL no info " + name);
                    fail++;
                    continue;
                }
                WzImageProperty vslotObj = info.getChild("vslot");
                WzImageProperty islotObj = info.getChild("islot");
                if (!(vslotObj instanceof WzStringProperty vslot) || !(islotObj instanceof WzStringProperty islot)) {
                    System.out.println("FAIL no islot/vslot " + name);
                    fail++;
                    continue;
                }
                boolean changed = false;
                String oldV = vslot.getValue();
                String oldI = islot.getValue();
                if (!NEW_VSLOT.equals(oldV)) {
                    vslot.setValue(NEW_VSLOT);
                    changed = true;
                }
                if (!NEW_ISLOT.equals(oldI)) {
                    islot.setValue(NEW_ISLOT);
                    changed = true;
                }
                if (!changed) {
                    skip++;
                    continue;
                }
                imgFile.setChanged(true);
                if (!imgFile.save()) {
                    System.out.println("FAIL save " + name + " islot=" + oldI + " vslot=" + oldV);
                    fail++;
                    continue;
                }
                ok++;
                System.out.println("OK " + name + " islot " + oldI + "->" + NEW_ISLOT + " vslotUpdated=" + !NEW_VSLOT.equals(oldV));
            } catch (Exception e) {
                fail++;
                System.out.println("FAIL " + name + " " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                imgFile.unparse();
            }
        }
        System.out.println("DONE ok=" + ok + " skip=" + skip + " fail=" + fail);
    }
}
