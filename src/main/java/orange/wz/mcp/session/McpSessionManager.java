package orange.wz.mcp.session;

import orange.wz.mcp.support.McpMemoryRelease;
import orange.wz.mcp.support.McpRootLeaseRegistry;
import orange.wz.provider.WzObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class McpSessionManager {
    // 客户端异常断开（崩溃/网络中断）时不会发送 DELETE 请求，会话若无 TTL 清理机制会一直占用内存
    // （每个会话持有的 roots 可能是完整解析过的 WZ 树，包含图片像素数据），故加入空闲超时回收
    private static final long IDLE_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(30);

    private final Map<UUID, McpSessionState> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mcp-session-cleanup");
                t.setDaemon(true);
                return t;
            });

    public McpSessionManager() {
        cleanupExecutor.scheduleWithFixedDelay(this::evictIdleSessions, 5, 5, TimeUnit.MINUTES);
    }

    public McpSessionState createSession() {
        UUID id = UUID.randomUUID();
        McpSessionState session = new McpSessionState(id);
        sessions.put(id, session);
        return session;
    }

    public McpSessionState getOrCreate(UUID id) {
        McpSessionState session = sessions.computeIfAbsent(id, McpSessionState::new);
        session.touch();
        return session;
    }

    public McpSessionState get(UUID id) {
        McpSessionState session = sessions.get(id);
        if (session != null) {
            session.touch();
        }
        return session;
    }

    public void remove(UUID id) {
        McpSessionState session = sessions.remove(id);
        if (session != null) {
            disposeSession(session);
        }
    }

    private void evictIdleSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> {
            if (now - entry.getValue().getLastAccessMillis() > IDLE_TIMEOUT_MILLIS) {
                disposeSession(entry.getValue());
                return true;
            }
            return false;
        });
    }

    private void disposeSession(McpSessionState session) {
        session.lock();
        try {
            List<WzObject> roots = new ArrayList<>(session.getRoots());
            session.getRoots().clear();
            for (WzObject root : roots) {
                McpMemoryRelease.dispose(root);
            }
            List<WzObject> clipboard = new ArrayList<>(session.getClipboard());
            session.getClipboard().clear();
            for (WzObject item : clipboard) {
                McpMemoryRelease.dispose(item);
            }
            session.getExclusiveLeases().clear();
            McpRootLeaseRegistry.get().releaseAll(session.getSessionId());
            McpMemoryRelease.hintGc();
        } finally {
            session.unlock();
        }
    }
}
