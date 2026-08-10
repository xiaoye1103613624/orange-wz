package orange.wz.mcp.tool.impl;

import orange.wz.mcp.service.McpWorkspaceService;
import orange.wz.mcp.session.McpSessionManager;
import orange.wz.mcp.tool.support.BaseSessionTool;
import orange.wz.mcp.tool.support.ToolParamHelper;

import java.util.List;
import java.util.Map;

import static orange.wz.mcp.tool.support.ToolSchemas.*;

public final class AnalyzeResourceLinksTool extends BaseSessionTool {
    private final McpWorkspaceService service;

    public AnalyzeResourceLinksTool(McpSessionManager sessionManager, McpWorkspaceService service) {
        super(sessionManager,
                "按 ID 检查已加载根中的跨资源关联（Item/Character/String/Mob…）、缺失伴生包与断裂 UOL/_outlink。",
                objectSchema(
                        Map.of(
                                "ids", arraySchema(stringSchema()),
                                "autoParse", booleanSchema(),
                                "maxUolChecks", numberSchema()
                        ),
                        List.of("ids")
                ));
        this.service = service;
    }

    @Override
    public String name() {
        return "analyze_resource_links";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> invoke(Map<String, Object> params) {
        var session = session(params);
        Object raw = params.get("ids");
        List<String> ids = raw instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        boolean autoParse = ToolParamHelper.getBoolean(params, "autoParse", true);
        int maxUolChecks = ToolParamHelper.getInt(params, "maxUolChecks", 64);
        return service.analyzeResourceLinks(session, ids, autoParse, maxUolChecks);
    }
}
