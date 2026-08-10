package orange.wz.mcp.service;

import orange.wz.mcp.dto.NodeSummary;
import orange.wz.mcp.dto.NodeDetail;
import orange.wz.mcp.dto.NodeReference;
import orange.wz.mcp.dto.OverwriteStrategy;
import orange.wz.mcp.session.McpSessionState;
import orange.wz.provider.WzObject;
import orange.wz.provider.tools.wzkey.WzKey;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface McpWorkspaceService {
    void loadFiles(McpSessionState session, List<File> files, WzKey key);

    /** When exclusive=true, refuse if another session already leased the same root path. */
    void loadFiles(McpSessionState session, List<File> files, WzKey key, boolean exclusive);

    void unloadNode(McpSessionState session, NodeReference reference);

    void unloadAll(McpSessionState session);

    /** Soft-clear decoded PNG / unparse unchanged imgs under all roots (+ optional clipboard). */
    void clearImageCaches(McpSessionState session, boolean clearClipboard);

    NodeSummary createWz(McpSessionState session, String fileName, short version, WzKey key);

    NodeSummary createImg(McpSessionState session, String fileName, WzKey key);

    List<NodeSummary> listLoadedRoots(McpSessionState session);

    WzObject findNode(McpSessionState session, NodeReference reference, boolean autoParse);

    List<NodeSummary> listChildren(McpSessionState session, NodeReference reference, boolean autoParse);

    void copyNodes(McpSessionState session, List<NodeReference> sources, boolean autoParse);

    List<NodeSummary> pasteToNode(McpSessionState session, NodeReference target, OverwriteStrategy strategy, boolean autoParse);

    List<NodeSummary> pasteToNode(McpSessionState session, NodeReference target, OverwriteStrategy strategy, boolean autoParse, boolean clearClipboard);

    /**
     * Atomic copy→paste under one write lock (no intermediate clipboard exposure to other tools).
     */
    Map<String, Object> copyPasteNodes(
            McpSessionState session,
            List<NodeReference> sources,
            List<NodeReference> targets,
            OverwriteStrategy strategy,
            boolean autoParse,
            boolean clearClipboard,
            boolean releaseSourceCache
    );

    NodeSummary createChildNode(
            McpSessionState session,
            NodeReference parent,
            String type,
            String name,
            String value,
            Integer x,
            Integer y,
            String base64Png,
            String base64Mp3,
            String pngFormat,
            boolean autoParse
    );

    void deleteNode(McpSessionState session, NodeReference reference, boolean autoParse);

    List<NodeSummary> searchNodeByName(McpSessionState session, NodeReference start, String keyword, boolean autoParse);

    List<Map<String, Object>> searchNodeByValue(McpSessionState session, NodeReference start, String keyword, boolean autoParse);

    NodeDetail getNodeDetail(McpSessionState session, NodeReference reference, boolean autoParse);

    Map<String, Object> getNodeTreeJson(McpSessionState session, NodeReference reference, boolean autoParse, int maxDepth);

    Map<String, Object> getNodeTreeJson(McpSessionState session, NodeReference reference, boolean autoParse, int maxDepth, boolean includePng);

    List<Map<String, Object>> batchFindNodes(McpSessionState session, List<Map<String, Object>> queries);

    List<Map<String, Object>> batchUpdateNodes(McpSessionState session, List<Map<String, Object>> operations);

    /**
     * @param continueOnError when true, each failed op becomes {ok:false,error} and later ops still run
     *                        under the same write lock (session-consistent ordering).
     */
    List<Map<String, Object>> batchUpdateNodes(McpSessionState session, List<Map<String, Object>> operations, boolean continueOnError);

    List<Map<String, Object>> listDirtyRoots(McpSessionState session);

    Map<String, Object> saveDirtyRoots(McpSessionState session, boolean unloadAfterSave, boolean clearCache);

    Map<String, Object> analyzeResourceLinks(McpSessionState session, List<String> ids, boolean autoParse, int maxUolChecks);

    Map<String, Object> verifyCanvasFormats(
            McpSessionState session,
            NodeReference reference,
            boolean autoParse,
            int maxReport,
            List<String> flagFormats
    );

    void saveNode(McpSessionState session, NodeReference reference, boolean autoParse);

    void saveNode(McpSessionState session, NodeReference reference, boolean autoParse, boolean unloadAfterSave, boolean clearCache);

    void saveNodeAs(McpSessionState session, NodeReference reference, String filePath, boolean autoParse);

    void saveNodeAs(McpSessionState session, NodeReference reference, String filePath, boolean autoParse, boolean unloadAfterSave, boolean clearCache);

    /**
     * Re-key a loaded root. When verify=true, compares decoded content fingerprints before/after.
     */
    Map<String, Object> changeKey(
            McpSessionState session,
            String rootPath,
            WzKey targetKey,
            Short targetWzVersion,
            boolean save,
            boolean verify
    );

    /**
     * Single-port parallel batch key conversion (isolated workers, no clipboard).
     */
    Map<String, Object> batchConvertKey(
            String sourceDir,
            String outputDir,
            String sourceRoot,
            List<String> paths,
            WzKey sourceKey,
            WzKey targetKey,
            Short targetWzVersion,
            int parallelism,
            boolean verify,
            boolean overwrite
    );
}
