package orange.wz.mcp.tool.impl;

import orange.wz.mcp.service.McpWorkspaceService;
import orange.wz.mcp.session.McpSessionManager;
import orange.wz.mcp.tool.support.BaseSessionTool;
import orange.wz.mcp.tool.support.ToolParamHelper;
import orange.wz.provider.tools.wzkey.WzKey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static orange.wz.mcp.tool.support.ToolSchemas.*;

/**
 * Single-port parallel key conversion. Does not use session clipboard;
 * each file is converted on an isolated worker thread with optional verify.
 */
public final class BatchConvertKeyTool extends BaseSessionTool {
    private final McpWorkspaceService service;

    public BatchConvertKeyTool(McpSessionManager sessionManager, McpWorkspaceService service) {
        super(sessionManager,
                "单端口并行批量密钥转换（.img/.wz）。不占用剪贴板；默认 fingerprint 校验失败则删除输出。parallelism 默认=CPU核数。",
                objectSchema(
                        Map.of(
                                "sourceDir", stringSchema(),
                                "outputDir", stringSchema(),
                                "sourceKey", keySchema(),
                                "targetKey", keySchema(),
                                "paths", arraySchema(stringSchema()),
                                "sourceRoot", stringSchema(),
                                "targetVersion", numberSchema(),
                                "parallelism", numberSchema(),
                                "verify", booleanSchema(),
                                "overwrite", booleanSchema()
                        ),
                        List.of("outputDir", "sourceKey", "targetKey")
                ));
        this.service = service;
    }

    @Override
    public String name() {
        return "batch_convert_key";
    }

    @Override
    public Map<String, Object> invoke(Map<String, Object> params) {
        // sessionId still required for MCP auth/session lifecycle; conversion itself is isolated.
        session(params);
        String sourceDir = ToolParamHelper.getString(params, "sourceDir", null);
        String outputDir = ToolParamHelper.requireString(params, "outputDir");
        String sourceRoot = ToolParamHelper.getString(params, "sourceRoot", null);
        List<String> paths = ToolParamHelper.getStringList(params, "paths");
        WzKey sourceKey = ToolParamHelper.getWzKey(params, "sourceKey");
        WzKey targetKey = ToolParamHelper.getWzKey(params, "targetKey");
        Short targetVersion = params.get("targetVersion") == null
                ? null
                : ToolParamHelper.getShort(params, "targetVersion", (short) -1);
        int parallelism = ToolParamHelper.getInt(params, "parallelism",
                Math.max(1, Runtime.getRuntime().availableProcessors()));
        boolean verify = ToolParamHelper.getBoolean(params, "verify", true);
        boolean overwrite = ToolParamHelper.getBoolean(params, "overwrite", false);

        Map<String, Object> result = service.batchConvertKey(
                sourceDir, outputDir, sourceRoot, paths,
                sourceKey, targetKey, targetVersion, parallelism, verify, overwrite
        );
        return new HashMap<>(result);
    }
}
