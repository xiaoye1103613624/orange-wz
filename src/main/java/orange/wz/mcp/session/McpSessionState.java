package orange.wz.mcp.session;

import orange.wz.provider.WzObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class McpSessionState {
    private final UUID sessionId;
    private final List<WzObject> roots = new ArrayList<>();
    private final List<WzObject> clipboard = new ArrayList<>();
    private final Set<String> exclusiveLeases = new LinkedHashSet<>();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    // 记录最近一次访问时间，供 McpSessionManager 清理长期无人访问（客户端异常断开未发 DELETE）的会话，
    // 避免每个会话持有的整棵 WzObject 树（含已解析的图片数据）无限累积造成内存泄漏
    private volatile long lastAccessMillis = System.currentTimeMillis();
    /** Bumps on every write mutation — callers may pass expectedGeneration for optimistic checks. */
    private final AtomicLong generation = new AtomicLong(0);

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

    public long getGeneration() {
        return generation.get();
    }

    public long bumpGeneration() {
        return generation.incrementAndGet();
    }

    public List<WzObject> getRoots() {
        return roots;
    }

    public List<WzObject> getClipboard() {
        return clipboard;
    }

    public Set<String> getExclusiveLeases() {
        return exclusiveLeases;
    }

    /** Exclusive write lock (roots / clipboard mutation). */
    public void lock() {
        lockWrite();
    }

    public void unlock() {
        unlockWrite();
    }

    public void lockWrite() {
        rwLock.writeLock().lock();
    }

    public void unlockWrite() {
        rwLock.writeLock().unlock();
    }

    /** Shared read lock — concurrent queries OK; blocked by any writer. */
    public void lockRead() {
        rwLock.readLock().lock();
    }

    public void unlockRead() {
        rwLock.readLock().unlock();
    }
}
