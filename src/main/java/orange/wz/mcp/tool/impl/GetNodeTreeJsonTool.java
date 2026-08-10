package orange.wz.mcp.tool.impl;

import orange.wz.mcp.service.McpWorkspaceService;
import orange.wz.mcp.session.McpSessionManager;
import orange.wz.mcp.tool.support.BaseSessionTool;
import orange.wz.mcp.tool.support.ToolParamHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static orange.wz.mcp.tool.support.ToolSchemas.*;

public final class GetNodeTreeJsonTool extends BaseSessionTool {
    private final McpWorkspaceService service;

    public GetNodeTreeJsonTool(McpSessionManager sessionManager, McpWorkspaceService service) {
        super(sessionManager,
                "获取节点 JSON 树。默认 includePng=false 以避免 bulk 列表时把 canvas 编成 base64 撑爆堆；需要像素时显式传 includePng=true。",
                objectSchema(
                        Map.of(
                                "rootPath", stringSchema(),
                                "nodePath", stringSchema(),
                                "autoParse", booleanSchema(),
                                "maxDepth", numberSchema(),
                                "includePng", booleanSchema(),
                                "nodes", arraySchema(nodeReferenceWithReadOptionsSchema())
                        ),
                        List.of()
                ));
        this.service = service;
    }

    @Override
    public String name() {
        return "get_node_tree_json";
    }

    @Override
    public Map<String, Object> invoke(Map<String, Object> params) {
        var session = session(params);
        var nodes = ToolParamHelper.getObjectList(params, "nodes");
        if (params.containsKey("nodes")) {
            boolean defaultAutoParse = ToolParamHelper.getBoolean(params, "autoParse", true);
            int defaultMaxDepth = ToolParamHelper.getInt(params, "maxDepth", 0);
            boolean defaultIncludePng = ToolParamHelper.getBoolean(params, "includePng", false);
            var results = new ArrayList<Map<String, Object>>(nodes.size());
            for (Map<String, Object> node : nodes) {
                var reference = ToolParamHelper.getNodeReference(node);
                boolean autoParse = ToolParamHelper.getBoolean(node, "autoParse", defaultAutoParse);
                int maxDepth = ToolParamHelper.getInt(node, "maxDepth", defaultMaxDepth);
                boolean includePng = ToolParamHelper.getBoolean(node, "includePng", defaultIncludePng);
                results.add(Map.of(
                        "rootPath", reference.rootPath(),
                        "nodePath", reference.nodePath(),
                        "tree", service.getNodeTreeJson(session, reference, autoParse, maxDepth, includePng)
                ));
            }
            return Map.of("results", results);
        }

        boolean autoParse = ToolParamHelper.getBoolean(params, "autoParse", true);
        int maxDepth = ToolParamHelper.getInt(params, "maxDepth", 0);
        boolean includePng = ToolParamHelper.getBoolean(params, "includePng", false);
        return Map.of("tree", service.getNodeTreeJson(session, ToolParamHelper.getNodeReference(params), autoParse, maxDepth, includePng));
    }
}
