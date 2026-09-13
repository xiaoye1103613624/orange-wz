package orange.wz.mcp.tool.impl;

import orange.wz.mcp.service.McpWorkspaceService;
import orange.wz.mcp.session.McpSessionManager;
import orange.wz.mcp.tool.support.BaseSessionTool;
import orange.wz.mcp.tool.support.ToolParamHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static orange.wz.mcp.tool.support.ToolSchemas.*;

public final class MutateNodesTool extends BaseSessionTool {
    private final McpWorkspaceService service;

    public MutateNodesTool(McpSessionManager sessionManager, McpWorkspaceService service) {
        super(sessionManager, "统一节点写入入口。直接传单项返回 result+results；传 operations 数组返回 results。支持 create_child、delete、rename、set_value、set_vector、set_png（base64Png 或 filePath 二选一）、set_sound、save、save_as。continueOnError=true 时单条失败不中断批次（仍同一写锁顺序执行）。", objectSchema(
                Map.ofEntries(
                        Map.entry("operations", arraySchema(updateOperationSchema())),
                        Map.entry("continueOnError", booleanSchema()),
                        Map.entry("rootPath", stringSchema()),
                        Map.entry("nodePath", stringSchema()),
                        Map.entry("op", stringSchema()),
                        Map.entry("autoParse", booleanSchema()),
                        Map.entry("type", stringSchema()),
                        Map.entry("name", stringSchema()),
                        Map.entry("value", stringSchema()),
                        Map.entry("x", numberSchema()),
                        Map.entry("y", numberSchema()),
                        Map.entry("base64Png", stringSchema()),
                        Map.entry("base64Mp3", stringSchema()),
                        Map.entry("pngFormat", stringSchema()),
                        Map.entry("filePath", stringSchema())
                ),
                List.of()
        ));
        this.service = service;
    }

    @Override
    public String name() {
        return "mutate_nodes";
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> invoke(Map<String, Object> params) {
        var session = session(params);
        Object operations = params.get("operations");
        List<Map<String, Object>> list;
        boolean batch = operations instanceof List<?>;
        if (operations instanceof List<?> raw) {
            list = (List<Map<String, Object>>) raw;
        } else {
            Map<String, Object> operation = new HashMap<>(params);
            operation.remove("sessionId");
            operation.remove("continueOnError");
            list = List.of(operation);
        }
        boolean continueOnError = ToolParamHelper.getBoolean(params, "continueOnError", false);
        var results = service.batchUpdateNodes(session, list, continueOnError);
        if (!batch && !results.isEmpty()) {
            return Map.of("result", results.get(0), "results", results, "generation", session.getGeneration());
        }
        return Map.of("results", results, "generation", session.getGeneration());
    }
}
