package orange.wz.provider.tools;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;

public final class WzMutableKey {
    private final byte[] iv;
    private final byte[] userKey;
    private final byte[] aesUserKey;
    // 该实例在密钥转换(changeKey)时会被同一个 WzFile 下所有 WzImage 并发共享读取，
    // volatile 保证多线程下扩容后能读到最新数组引用
    private volatile byte[] keys;
    private final Object growLock = new Object();

    private static final int batchSize = 4096;

    public WzMutableKey(byte[] iv, byte[] userKey) {
        this.iv = iv;
        this.userKey = userKey;
        this.aesUserKey = getTrimmedUserKey();
    }

    public byte get(int index) {
        try {
            // 快路径：keystream 已经够长时不加锁直接读，避免并行转换key时的锁竞争
            byte[] snapshot = keys;
            if (snapshot != null && snapshot.length > index) {
                return snapshot[index];
            }
            ensureKeySize(index + 1);
            return keys[index];
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private void ensureKeySize(int size) throws Exception {
        synchronized (growLock) {
            if (keys != null && keys.length >= size) {
                return;
            }

            size = (int) Math.ceil(1.0 * size / batchSize) * batchSize;
            byte[] newKeys = new byte[size];

            if (ByteBuffer.wrap(iv).getInt() == 0) {
                keys = newKeys;
                return;
            }

            int startIndex = 0;
            if (keys != null) {
                System.arraycopy(keys, 0, newKeys, 0, keys.length);
                startIndex = keys.length;
            }

            keys = newKeys;

            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(aesUserKey, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);

            byte[] block = new byte[16];

            for (int i = startIndex; i < size; i += 16) {
                if (i == 0) {
                    for (int j = 0; j < block.length; j++) {
                        block[j] = iv[j % 4];
                    }
                } else {
                    System.arraycopy(newKeys, i - 16, block, 0, 16);
                }

                byte[] enc = cipher.update(block);
                System.arraycopy(enc, 0, newKeys, i, 16);
            }

            keys = newKeys;
        }
    }

    private byte[] getTrimmedUserKey() {
        byte[] key = new byte[32];
        for (int i = 0; i < 128; i += 16) {
            key[i / 4] = userKey[i];
        }
        return key;
    }
}
