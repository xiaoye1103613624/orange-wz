package orange.wz.mcp.tool.impl;

import orange.wz.mcp.service.McpWorkspaceService;
import orange.wz.mcp.session.McpSessionManager;
import orange.wz.mcp.tool.support.BaseSessionTool;
import orange.wz.mcp.tool.support.ToolParamHelper;

import java.util.List;
import java.util.Map;

import static orange.wz.mcp.tool.support.ToolSchemas.*;

public final class UnloadAllTool extends BaseSessionTool {
    private final McpWorkspaceService service;

    public UnloadAllTool(McpSessionManager sessionManager, McpWorkspaceService service) {
        super(sessionManager,
                "卸载当前会话中的全部已加载对象，并 dispose 根对象/剪贴板以便 GC 回收。可选 hintGc。",
                objectSchema(Map.of("hintGc", booleanSchema()), List.of()));
        this.service = service;
    }

    @Override
    public String name() {
        return "unload_all";
    }

    @Override
    public Map<String, Object> invoke(Map<String, Object> params) {
        var session = session(params);
        service.unloadAll(session);
        // unloadAll already hints GC; allow explicit no-op flag for schema discoverability
        if (ToolParamHelper.getBoolean(params, "hintGc", true)) {
            // already called inside unloadAll
        }
        return Map.of("ok", true, "rootCount", 0);
    }
}
