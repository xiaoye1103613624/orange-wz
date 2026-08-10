package orange.wz.mcp.support;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpRootLeaseRegistryTest {
    @Test
    void exclusiveLeaseBlocksOtherSession() {
        McpRootLeaseRegistry reg = McpRootLeaseRegistry.get();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        String path = "E:/tmp/lease-test-" + a + "/0243.img";
        try {
            reg.acquireExclusive(a, path);
            assertThrows(McpException.class, () -> reg.acquireExclusive(b, path));
            assertDoesNotThrow(() -> reg.acquireExclusive(a, path));
        } finally {
            reg.releaseAll(a);
            reg.releaseAll(b);
        }
        assertTrue(reg.snapshot().isEmpty() || !reg.snapshot().containsKey(path));
    }
}
