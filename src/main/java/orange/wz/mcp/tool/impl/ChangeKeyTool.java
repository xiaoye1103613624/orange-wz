package orange.wz.mcp.tool.impl;

import orange.wz.mcp.service.McpWorkspaceService;
import orange.wz.mcp.session.McpSessionManager;
import orange.wz.mcp.tool.support.BaseSessionTool;
import orange.wz.mcp.tool.support.ToolParamHelper;
import orange.wz.provider.tools.wzkey.WzKey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static orange.wz.mcp.tool.support.ToolSchemas.*;

/**
 * Re-key a loaded session root (img/wz) in-place. Optional save + fingerprint verify.
 */
public final class ChangeKeyTool extends BaseSessionTool {
    private final McpWorkspaceService service;

    public ChangeKeyTool(McpSessionManager sessionManager, McpWorkspaceService service) {
        super(sessionManager, "对已加载的 wz/img 根节点更换密钥。可选 save/verify；verify 用解码后内容指纹确保准确无误。", objectSchema(
                Map.of(
                        "rootPath", stringSchema(),
                        "key", keySchema(),
                        "version", numberSchema(),
                        "save", booleanSchema(),
                        "verify", booleanSchema()
                ),
                List.of("rootPath", "key")
        ));
        this.service = service;
    }

    @Override
    public String name() {
        return "change_key";
    }

    @Override
    public Map<String, Object> invoke(Map<String, Object> params) {
        var session = session(params);
        String rootPath = ToolParamHelper.requireString(params, "rootPath");
        WzKey key = ToolParamHelper.getWzKey(params, "key");
        Short version = params.get("version") == null ? null : ToolParamHelper.getShort(params, "version", (short) -1);
        boolean save = ToolParamHelper.getBoolean(params, "save", false);
        boolean verify = ToolParamHelper.getBoolean(params, "verify", true);
        Map<String, Object> result = service.changeKey(session, rootPath, key, version, save, verify);
        return new HashMap<>(result);
    }
}
