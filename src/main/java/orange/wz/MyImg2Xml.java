package orange.wz;

import orange.wz.provider.WzImageFile;
import orange.wz.provider.tools.MediaExportType;
import orange.wz.provider.tools.XmlExport;

import java.nio.file.Path;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * 单文件 img -> xml 转换工具
 * 用法：java orange.wz.MyImg2Xml <源img路径> <输出xml路径>
 */
public class MyImg2Xml {
    public static void main(String[] args) {
        String srcImg = args[0];
        String dstXml = args[1];

        String name = Path.of(srcImg).getFileName().toString();
        WzImageFile imgFile = new WzImageFile(name, srcImg, "BeiDou", WZ_GMS_IV, DEFAULT_KEY);
        if (!imgFile.parse()) {
            System.err.println("解析失败: " + srcImg);
            System.exit(1);
        }
        boolean ok = new XmlExport(imgFile, 4, false, MediaExportType.BASE64).export(Path.of(dstXml));
        System.out.println(ok ? "OK: " + dstXml : "FAIL");
    }
}
