import orange.wz.provider.WzXmlFile;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static orange.wz.provider.WzAESConstant.DEFAULT_KEY;
import static orange.wz.provider.WzAESConstant.WZ_GMS_IV;

/**
 * 校验服务端 .img.xml 能否被 XmlImport 解析。
 * 用法: java ValidateXmlDir &lt;xml目录&gt;
 */
public class ValidateXmlDir {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("用法: java ValidateXmlDir <xml目录>");
            System.exit(1);
        }
        Path src = Path.of(args[0]);
        List<Path> xmls = new ArrayList<>();
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String n = file.getFileName().toString();
                if (n.endsWith(".img.xml") && !n.contains(".bak")) xmls.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        List<String> fails = Collections.synchronizedList(new ArrayList<>());
        System.out.println("校验 " + xmls.size() + " 个 .img.xml @ " + src);
        xmls.parallelStream().forEach(file -> {
            Path rel = src.relativize(file);
            String name = file.getFileName().toString();
            WzXmlFile xml = new WzXmlFile(name, file.toString(), "国际服务器(低版本)", WZ_GMS_IV, DEFAULT_KEY);
            try {
                if (xml.parse()) {
                    ok.incrementAndGet();
                } else {
                    fail.incrementAndGet();
                    if (fails.size() < 200) fails.add(rel.toString());
                }
            } catch (Throwable t) {
                fail.incrementAndGet();
                if (fails.size() < 200) fails.add(rel + " :: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            } finally {
                try { xml.unparse(); } catch (Throwable ignored) {}
            }
        });
        System.out.println("ok=" + ok.get() + " fail=" + fail.get());
        fails.forEach(s -> System.out.println("[XML_FAIL] " + s));
        System.out.println(fail.get() == 0 ? "RESULT: CLEAN" : "RESULT: ISSUES");
    }
}
