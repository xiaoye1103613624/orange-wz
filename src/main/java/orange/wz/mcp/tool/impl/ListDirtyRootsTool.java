package orange.wz.mcp.tool.impl;

import orange.wz.mcp.service.McpWorkspaceService;
import orange.wz.mcp.session.McpSessionManager;
import orange.wz.mcp.tool.support.BaseSessionTool;

import java.util.Map;

import static orange.wz.mcp.tool.support.ToolSchemas.*;

public final class ListDirtyRootsTool extends BaseSessionTool {
    private final McpWorkspaceService service;

    public ListDirtyRootsTool(McpSessionManager sessionManager, McpWorkspaceService service) {
        super(sessionManager, "列出会话内已修改（dirty）的根节点，便于统一 save_dirty_roots。", emptyObjectSchema());
        this.service = service;
    }

    @Override
    public String name() {
        return "list_dirty_roots";
    }

    @Override
    public Map<String, Object> invoke(Map<String, Object> params) {
        var session = session(params);
        return Map.of(
                "dirty", service.listDirtyRoots(session),
                "generation", session.getGeneration()
        );
    }
}
