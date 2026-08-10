package orange.wz.mcp.tool.impl;

import orange.wz.mcp.service.McpWorkspaceService;
import orange.wz.mcp.session.McpSessionManager;
import orange.wz.mcp.tool.support.BaseSessionTool;
import orange.wz.mcp.tool.support.ToolParamHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static orange.wz.mcp.tool.support.ToolSchemas.*;

public final class SaveNodeAsTool extends BaseSessionTool {
    private final McpWorkspaceService service;

    public SaveNodeAsTool(McpSessionManager sessionManager, McpWorkspaceService service) {
        super(sessionManager,
                "将指定文件节点另存为到目标路径。可选 unloadAfterSave / clearCache。",
                objectSchema(
                        Map.of(
                                "rootPath", stringSchema(),
                                "nodePath", stringSchema(),
                                "filePath", stringSchema(),
                                "autoParse", booleanSchema(),
                                "unloadAfterSave", booleanSchema(),
                                "clearCache", booleanSchema()
                        ),
                        List.of("rootPath", "filePath")
                ));
        this.service = service;
    }

    @Override
    public String name() {
        return "save_as";
    }

    @Override
    public Map<String, Object> invoke(Map<String, Object> params) {
        var session = session(params);
        String filePath = ToolParamHelper.requireString(params, "filePath");
        boolean autoParse = ToolParamHelper.getBoolean(params, "autoParse", true);
        boolean unloadAfterSave = ToolParamHelper.getBoolean(params, "unloadAfterSave", false);
        boolean clearCache = ToolParamHelper.getBoolean(params, "clearCache", false);
        service.saveNodeAs(session, ToolParamHelper.getNodeReference(params), filePath, autoParse, unloadAfterSave, clearCache);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("unloadAfterSave", unloadAfterSave);
        result.put("clearCache", clearCache);
        result.put("rootCount", session.getRoots().size());
        return result;
    }
}
