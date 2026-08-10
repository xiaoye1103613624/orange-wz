package orange.wz.mcp.tool.impl;

import orange.wz.mcp.service.McpWorkspaceService;
import orange.wz.mcp.session.McpSessionManager;
import orange.wz.mcp.tool.support.BaseSessionTool;
import orange.wz.mcp.tool.support.ToolParamHelper;

import java.util.Map;

import static orange.wz.mcp.tool.support.ToolSchemas.*;

public final class SaveDirtyRootsTool extends BaseSessionTool {
    private final McpWorkspaceService service;

    public SaveDirtyRootsTool(McpSessionManager sessionManager, McpWorkspaceService service) {
        super(sessionManager,
                "保存会话内全部 dirty 根。可选 unloadAfterSave / clearCache。保证同一次写锁内顺序落盘。",
                objectSchema(
                        Map.of(
                                "unloadAfterSave", booleanSchema(),
                                "clearCache", booleanSchema()
                        ),
                        java.util.List.of()
                ));
        this.service = service;
    }

    @Override
    public String name() {
        return "save_dirty_roots";
    }

    @Override
    public Map<String, Object> invoke(Map<String, Object> params) {
        var session = session(params);
        boolean unloadAfterSave = ToolParamHelper.getBoolean(params, "unloadAfterSave", false);
        boolean clearCache = ToolParamHelper.getBoolean(params, "clearCache", true);
        return service.saveDirtyRoots(session, unloadAfterSave, clearCache);
    }
}
