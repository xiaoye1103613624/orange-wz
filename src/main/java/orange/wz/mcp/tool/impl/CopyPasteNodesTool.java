package orange.wz.mcp.tool.impl;

import orange.wz.mcp.dto.NodeReference;
import orange.wz.mcp.dto.OverwriteStrategy;
import orange.wz.mcp.service.McpWorkspaceService;
import orange.wz.mcp.session.McpSessionManager;
import orange.wz.mcp.tool.support.BaseSessionTool;
import orange.wz.mcp.tool.support.ToolParamHelper;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static orange.wz.mcp.tool.support.ToolSchemas.*;

public final class CopyPasteNodesTool extends BaseSessionTool {
    private final McpWorkspaceService service;

    public CopyPasteNodesTool(McpSessionManager sessionManager, McpWorkspaceService service) {
        super(sessionManager,
                "原子 copy→paste：同一写锁内完成，避免剪贴板窗口期竞态。支持多源多目标；可选 releaseSourceCache 软释放源图缓存。",
                objectSchema(
                        Map.of(
                                "sources", arraySchema(nodeReferenceSchema()),
                                "targets", arraySchema(nodeReferenceSchema()),
                                "strategy", stringSchema(),
                                "autoParse", booleanSchema(),
                                "clearClipboard", booleanSchema(),
                                "releaseSourceCache", booleanSchema()
                        ),
                        List.of("sources", "targets")
                ));
        this.service = service;
    }

    @Override
    public String name() {
        return "copy_paste_nodes";
    }

    @Override
    public Map<String, Object> invoke(Map<String, Object> params) {
        var session = session(params);
        List<NodeReference> sources = ToolParamHelper.getNodeReferenceList(params, "sources");
        List<NodeReference> targets = ToolParamHelper.getNodeReferenceList(params, "targets");
        boolean autoParse = ToolParamHelper.getBoolean(params, "autoParse", true);
        boolean clearClipboard = ToolParamHelper.getBoolean(params, "clearClipboard", true);
        boolean releaseSourceCache = ToolParamHelper.getBoolean(params, "releaseSourceCache", true);
        String strategyText = ToolParamHelper.getString(params, "strategy", OverwriteStrategy.ERROR.name());
        OverwriteStrategy strategy = OverwriteStrategy.valueOf(strategyText.toUpperCase(Locale.ROOT));
        return service.copyPasteNodes(session, sources, targets, strategy, autoParse, clearClipboard, releaseSourceCache);
    }
}
