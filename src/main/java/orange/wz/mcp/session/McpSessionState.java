package orange.wz.mcp.session;

import orange.wz.provider.WzObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public final class McpSessionState {
    private final UUID sessionId;
    private final List<WzObject> roots = new ArrayList<>();
    private final List<WzObject> clipboard = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    // 记录最近一次访问时间，供 McpSessionManager 清理长期无人访问（客户端异常断开未发 DELETE）的会话，
    // 避免每个会话持有的整棵 WzObject 树（含已解析的图片数据）无限累积造成内存泄漏
    private volatile long lastAccessMillis = System.currentTimeMillis();

    public McpSessionState(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void touch() {
        lastAccessMillis = System.currentTimeMillis();
    }

    public long getLastAccessMillis() {
        return lastAccessMillis;
    }

    public List<WzObject> getRoots() {
        return roots;
    }

    public List<WzObject> getClipboard() {
        return clipboard;
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }
}
