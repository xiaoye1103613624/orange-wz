import orange.wz.provider.WzXmlFile;
import orange.wz.provider.tools.XmlImport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * 将「内容为 XML、扩展名为 .img」的文件转为真正的二进制 .img。
 * 用法: java BatchXmlImgToBin &lt;list.txt&gt; &lt;Character根目录&gt;
 * list.txt 每行相对路径，如 Accessory\01142180.img
 */
public class BatchXmlImgToBin {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("用法: java BatchXmlImgToBin <list.txt> <Character根目录>");
            System.exit(1);
        }
        Path list = Path.of(args[0]);
        Path root = Path.of(args[1]);
        List<String> rels = Files.readAllLines(list).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty() && s.endsWith(".img"))
                .toList();
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        AtomicInteger skip = new AtomicInteger();
        System.out.println("待处理: " + rels.size());

        int n = Math.max(1, Runtime.getRuntime().availableProcessors());
        ExecutorService pool = Executors.newFixedThreadPool(n);
        for (String rel : rels) {
            pool.submit(() -> {
                Path file = root.resolve(rel);
                try {
                    if (!Files.exists(file)) {
                        System.err.println("[MISS] " + rel);
                        fail.incrementAndGet();
                        return;
                    }
                    byte[] head = Files.readAllBytes(file);
                    if (head.length < 5 || !(head[0] == '<' && head[1] == '?' && head[2] == 'x')) {
                        skip.incrementAndGet();
                        return;
                    }
                    // 先落到临时 .xml 再 import（避免扩展名干扰）
                    Path tmpXml = Files.createTempFile("wzfix_", ".img.xml");
                    try {
                        Files.write(tmpXml, head);
                        WzXmlFile xml = XmlImport.importXml(tmpXml, "国际服务器(低版本)", WZ_GMS_IV, DEFAULT_KEY);
                        if (xml == null) {
                            System.err.println("[FAIL import] " + rel);
                            fail.incrementAndGet();
                            return;
                        }
                        // 节点名应为 xxx.img
                        String imgName = file.getFileName().toString();
                        xml.setName(imgName);
                        if (!xml.save(file)) {
                            System.err.println("[FAIL save] " + rel);
                            fail.incrementAndGet();
                            return;
                        }
                        System.out.println("[OK] " + rel + " -> " + Files.size(file) + "B");
                        ok.incrementAndGet();
                    } finally {
                        Files.deleteIfExists(tmpXml);
                    }
                } catch (Throwable t) {
                    System.err.println("[ERR] " + rel + " :: " + t.getMessage());
                    fail.incrementAndGet();
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.HOURS);
        System.out.printf("完成 ok=%d fail=%d skip(非XML)=%d%n", ok.get(), fail.get(), skip.get());
    }
}
