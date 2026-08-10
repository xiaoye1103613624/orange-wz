package orange.wz.mcp.tool.impl;

import orange.wz.mcp.service.McpWorkspaceService;
import orange.wz.mcp.session.McpSessionManager;
import orange.wz.mcp.tool.support.BaseSessionTool;
import orange.wz.mcp.tool.support.ToolParamHelper;

import java.util.List;
import java.util.Map;

import static orange.wz.mcp.tool.support.ToolSchemas.*;

/**
 * Soft-clear decoded PNG caches / unparse unchanged imgs without unloading roots.
 * Useful between bulk copy/paste/save loops when a source WZ must stay loaded.
 */
public final class ClearCacheTool extends BaseSessionTool {
    private final McpWorkspaceService service;

    public ClearCacheTool(McpSessionManager sessionManager, McpWorkspaceService service) {
        super(sessionManager,
                "软释放已解析图片缓存（解码 PNG / 未改动 img 的 unparse），可选清空剪贴板。不卸载已加载根。",
                objectSchema(
                        Map.of(
                                "clearClipboard", booleanSchema(),
                                "hintGc", booleanSchema()
                        ),
                        List.of()
                ));
        this.service = service;
    }

    @Override
    public String name() {
        return "clear_cache";
    }

    @Override
    public Map<String, Object> invoke(Map<String, Object> params) {
        var session = session(params);
        boolean clearClipboard = ToolParamHelper.getBoolean(params, "clearClipboard", true);
        service.clearImageCaches(session, clearClipboard);
        return Map.of(
                "ok", true,
                "rootCount", session.getRoots().size(),
                "clipboardCleared", clearClipboard
        );
    }
}
