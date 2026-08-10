package orange.wz;

import orange.wz.provider.WzXmlFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * Batch xml -&gt; img. List file lines: srcXml|dstImg|name
 */
public class BatchXml2Img {
    public static void main(String[] args) throws Exception {
        Path list = Path.of(args[0]);
        int ok = 0;
        int fail = 0;
        for (String line : Files.readAllLines(list)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] p = line.split("\\|");
            try {
                Path dst = Path.of(p[1]);
                Files.createDirectories(dst.getParent());
                WzXmlFile xmlFile = new WzXmlFile(p[2], p[0], "", WZ_GMS_IV, DEFAULT_KEY);
                xmlFile.saveFromXml(dst);
                ok++;
                System.out.println("OK " + p[2] + " " + Files.size(dst));
            } catch (Exception e) {
                fail++;
                System.out.println("FAIL " + p[2] + " " + e.getMessage());
            }
        }
        System.out.println("DONE ok=" + ok + " fail=" + fail);
    }
}
