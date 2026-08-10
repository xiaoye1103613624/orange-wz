package orange.wz.mcp.tool.impl;

import orange.wz.mcp.service.McpWorkspaceService;
import orange.wz.mcp.session.McpSessionManager;
import orange.wz.mcp.tool.support.BaseSessionTool;
import orange.wz.mcp.tool.support.ToolParamHelper;

import java.util.List;
import java.util.Map;

import static orange.wz.mcp.tool.support.ToolSchemas.*;

public final class VerifyCanvasFormatsTool extends BaseSessionTool {
    private final McpWorkspaceService service;

    public VerifyCanvasFormatsTool(McpSessionManager sessionManager, McpWorkspaceService service) {
        super(sessionManager,
                "扫描节点树 canvas 格式。默认标记 ARGB8888（v083 易导致不正确游戏数据）。可用 flagFormats 自定义。",
                objectSchema(
                        Map.of(
                                "rootPath", stringSchema(),
                                "nodePath", stringSchema(),
                                "autoParse", booleanSchema(),
                                "maxReport", numberSchema(),
                                "flagFormats", arraySchema(stringSchema())
                        ),
                        List.of("rootPath")
                ));
        this.service = service;
    }

    @Override
    public String name() {
        return "verify_canvas_formats";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> invoke(Map<String, Object> params) {
        var session = session(params);
        boolean autoParse = ToolParamHelper.getBoolean(params, "autoParse", true);
        int maxReport = ToolParamHelper.getInt(params, "maxReport", 50);
        Object raw = params.get("flagFormats");
        List<String> flagFormats = raw instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        return service.verifyCanvasFormats(
                session,
                ToolParamHelper.getNodeReference(params),
                autoParse,
                maxReport,
                flagFormats
        );
    }
}
