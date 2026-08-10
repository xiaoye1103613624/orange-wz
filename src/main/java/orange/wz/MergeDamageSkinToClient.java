package orange.wz;

import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.WzXmlFile;
import java.nio.file.Path;
import static orange.wz.provider.WzAESConstant.*;

public class MergeDamageSkinToClient {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("ERROR:usage <serverXml> <clientImg> <nodeName>");
            System.exit(1);
        }
        String serverXml = args[0];
        String clientImg = args[1];
        String nodeName = args[2];
        String clientName = Path.of(clientImg).getFileName().toString();

        WzXmlFile src = new WzXmlFile(Path.of(serverXml).getFileName().toString(), serverXml, "083-GMS", WZ_GMS_IV, DEFAULT_KEY);
        if (!src.parse()) { System.out.println("ERROR:parse server"); System.exit(2); }
        WzImageProperty srcNode = src.getChild(nodeName);
        if (srcNode == null) { System.out.println("ERROR:missing node " + nodeName); System.exit(3); }

        WzImageFile dst = new WzImageFile(clientName, clientImg, "083-GMS", WZ_GMS_IV, DEFAULT_KEY);
        if (!dst.parse()) { System.out.println("ERROR:parse client"); System.exit(4); }

        if (dst.getChild(nodeName) != null) dst.removeChild(nodeName);
        WzImageProperty clone = srcNode.deepClone(dst);
        dst.addChild(clone);
        clone.setWzImage(dst);
        clone.setChildrenWzImage(dst);
        dst.setChanged(true);
        dst.setTempChanged(true);

        if (!dst.save(Path.of(clientImg))) {
            System.out.println("ERROR:save failed");
            System.exit(5);
        }
        System.out.println("MERGED:" + nodeName + " size=" + Path.of(clientImg).toFile().length());
    }
}