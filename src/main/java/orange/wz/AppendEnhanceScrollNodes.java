package orange.wz;

import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzXmlFile;
import orange.wz.provider.tools.MediaExportType;
import orange.wz.provider.tools.XmlExport;
import orange.wz.provider.tools.XmlImport;

import java.nio.file.Files;
import java.nio.file.Path;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * Append 02439101/02439102/02439103 to an existing 0243.img without losing embedded canvas data.
 */
public class AppendEnhanceScrollNodes {
    private static final String NODE_BLOCK = """
    <imgdir name="02439101">
        <imgdir name="info">
            <uol name="icon" value="../0204.img/02040023/info/icon"/>
            <uol name="iconRaw" value="../0204.img/02040023/info/iconRaw"/>
            <int name="price" value="1"/>
            <int name="slotMax" value="100"/>
            <int name="tradeBlock" value="1"/>
        </imgdir>
    </imgdir>
    <imgdir name="02439102">
        <imgdir name="info">
            <uol name="icon" value="../0204.img/02040019/info/icon"/>
            <uol name="iconRaw" value="../0204.img/02040019/info/iconRaw"/>
            <int name="price" value="1"/>
            <int name="slotMax" value="100"/>
            <int name="tradeBlock" value="1"/>
        </imgdir>
    </imgdir>
    <imgdir name="02439103">
        <imgdir name="info">
            <uol name="icon" value="../0204.img/02040020/info/icon"/>
            <uol name="iconRaw" value="../0204.img/02040020/info/iconRaw"/>
            <int name="price" value="1"/>
            <int name="slotMax" value="100"/>
            <int name="tradeBlock" value="1"/>
        </imgdir>
    </imgdir>
    """;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: <srcImg> <outImg>");
            System.exit(2);
        }
        Path src = Path.of(args[0]);
        Path out = Path.of(args[1]);
        Path workXml = out.resolveSibling(out.getFileName() + ".work.xml");

        String name = src.getFileName().toString();
        WzImageFile imgFile = new WzImageFile(name, src.toString(), "BeiDou", WZ_GMS_IV, DEFAULT_KEY);
        if (!imgFile.parse()) {
            System.err.println("parse fail: " + src);
            System.exit(1);
        }
        if (!new XmlExport(imgFile, 2, false, MediaExportType.BASE64).export(workXml)) {
            System.err.println("export fail");
            System.exit(1);
        }

        String content = Files.readString(workXml);
        if (content.contains("02439101")) {
            System.out.println("SKIP: nodes already present");
            Files.copy(src, out);
            System.out.println("size=" + Files.size(out));
            return;
        }
        int idx = content.lastIndexOf("</imgdir>");
        if (idx < 0) {
            System.err.println("invalid xml");
            System.exit(1);
        }
        content = content.substring(0, idx) + NODE_BLOCK + content.substring(idx);
        Files.writeString(workXml, content);

        WzXmlFile wzXml = XmlImport.importXml(workXml, "BeiDou", WZ_GMS_IV, DEFAULT_KEY);
        if (wzXml == null || !wzXml.saveFromXml(out)) {
            System.err.println("import fail");
            System.exit(1);
        }
        System.out.println("OK size=" + Files.size(out));
    }
}
