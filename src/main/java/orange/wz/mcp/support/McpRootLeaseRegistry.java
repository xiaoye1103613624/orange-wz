package orange.wz.mcp.support;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-session exclusive lease for filesystem root paths.
 * Prevents two MCP sessions from mutating the same live .img/.wz concurrently
 * (clipboard races and half-saved packs).
 */
public final class McpRootLeaseRegistry {
    private static final McpRootLeaseRegistry INSTANCE = new McpRootLeaseRegistry();

    private final ConcurrentHashMap<String, UUID> leases = new ConcurrentHashMap<>();

    private McpRootLeaseRegistry() {
    }

    public static McpRootLeaseRegistry get() {
        return INSTANCE;
    }

    public void acquireExclusive(UUID sessionId, String normalizedRootPath) {
        if (sessionId == null || normalizedRootPath == null || normalizedRootPath.isBlank()) {
            return;
        }
        UUID existing = leases.putIfAbsent(normalizedRootPath, sessionId);
        if (existing != null && !existing.equals(sessionId)) {
            throw new McpException(
                    "根路径已被其它会话独占加载，拒绝并发写: " + normalizedRootPath
                            + " (holderSession=" + existing + ")"
            );
        }
        leases.put(normalizedRootPath, sessionId);
    }

    public void release(UUID sessionId, String normalizedRootPath) {
        if (sessionId == null || normalizedRootPath == null) {
            return;
        }
        leases.computeIfPresent(normalizedRootPath, (path, holder) ->
                holder.equals(sessionId) ? null : holder
        );
    }

    public void releaseAll(UUID sessionId) {
        if (sessionId == null) {
            return;
        }
        leases.entrySet().removeIf(e -> sessionId.equals(e.getValue()));
    }

    public Map<String, UUID> snapshot() {
        return Map.copyOf(leases);
    }
}
