package orange.wz;

import orange.wz.provider.WzXmlFile;

import java.nio.file.Path;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * 单文件 xml -> img 转换工具
 * 用法：java orange.wz.MyXml2Img <源xml路径> <输出img路径> <img文件名,例如0243.img>
 */
public class MyXml2Img {
    public static void main(String[] args) {
        String srcXml = args[0];
        String dstImg = args[1];
        String name = args[2];

        WzXmlFile xmlFile = new WzXmlFile(name, srcXml, "", WZ_GMS_IV, DEFAULT_KEY);
        xmlFile.saveFromXml(Path.of(dstImg));
        System.out.println("OK: " + dstImg);
    }
}
