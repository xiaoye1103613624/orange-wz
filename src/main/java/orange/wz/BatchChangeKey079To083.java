package orange.wz;

import orange.wz.provider.WzAESConstant;
import orange.wz.provider.tools.keyconvert.KeyConvertService;
import orange.wz.provider.tools.wzkey.WzKey;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 将 079 客户端(.img/.wz, CMS 区密钥)批量转换为 083 客户端(GMS 区密钥)。
 * 单进程多线程并行；默认指纹校验，失败自动删除输出，保证数据准确。
 * 用法：java -cp xxx orange.wz.BatchChangeKey079To083 &lt;079源目录&gt; &lt;083输出目录&gt; [异常日志] [parallelism]
 */
public class BatchChangeKey079To083 {

    private static final AtomicBoolean cancelled = new AtomicBoolean(false);

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("用法: java -cp xxx orange.wz.BatchChangeKey079To083 <079源目录> <083输出目录> [异常日志路径] [parallelism]");
            System.out.println("  默认 verify=true；并行度默认=CPU核数");
            System.exit(1);
        }
        Path src = Path.of(args[0]);
        Path dst = Path.of(args[1]);
        Path logPath = Path.of(args.length > 2 ? args[2] : "change_key_079_to_083_error.log");
        int parallelism = args.length > 3
                ? Math.max(1, Integer.parseInt(args[3]))
                : Math.max(1, Runtime.getRuntime().availableProcessors());

        if (!Files.exists(src)) {
            System.err.println("源目录不存在: " + src);
            System.exit(1);
        }

        KeyConvertService service = new KeyConvertService();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!cancelled.get()) {
                cancelled.set(true);
                service.cancel();
                System.out.println("\n收到中断信号，正在停止...");
            }
        }, "ShutdownHook"));

        WzKey sourceKey = new WzKey(-1, "079-CMS", WzAESConstant.WZ_CMS_IV, WzAESConstant.DEFAULT_KEY);
        WzKey targetKey = new WzKey(-1, "083-GMS", WzAESConstant.WZ_GMS_IV, WzAESConstant.DEFAULT_KEY);
        KeyConvertService.ConvertRequest request = new KeyConvertService.ConvertRequest(
                src, dst, sourceKey, targetKey, null, parallelism, true, true
        );

        System.out.printf("开始转换 source=%s dest=%s workers=%d verify=true%n", src, dst, parallelism);
        KeyConvertService.BatchResult result = service.convertTree(request);

        try (PrintWriter logWriter = new PrintWriter(Files.newBufferedWriter(logPath, StandardCharsets.UTF_8))) {
            for (KeyConvertService.FileResult fr : result.results()) {
                if (!fr.success()) {
                    logWriter.println(fr.relativePath() + " => " + fr.message());
                    System.err.println("[FAIL] " + fr.relativePath() + " => " + fr.message());
                }
            }
        }

        System.out.printf("%n密钥转换完成！成功: %d  失败: %d  总计: %d  耗时: %.1fs  异常日志: %s%n",
                result.success(), result.failure(), result.total(), result.elapsedMs() / 1000.0, logPath);
        if (result.failure() > 0) {
            System.exit(2);
        }
    }
}
