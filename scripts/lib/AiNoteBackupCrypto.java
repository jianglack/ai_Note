import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class AiNoteBackupCrypto {
    private static final byte[] MAGIC = "AINOTE-BACKUP-AESGCM-1".getBytes(StandardCharsets.US_ASCII);
    private static final int CHUNK_SIZE = 4 * 1024 * 1024;
    private static final int TAG_BITS = 128;

    private AiNoteBackupCrypto() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4 || !(args[0].equals("encrypt") || args[0].equals("decrypt"))) {
            throw new IllegalArgumentException("usage: encrypt|decrypt input output keyEnvironmentVariable");
        }
        byte[] key = readKey(args[3]);
        Path input = Path.of(args[1]);
        Path output = Path.of(args[2]);
        try {
            if (args[0].equals("encrypt")) {
                encrypt(input, output, key);
            } else {
                decrypt(input, output, key);
            }
        } catch (Exception failure) {
            Files.deleteIfExists(output);
            throw failure;
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static byte[] readKey(String variable) {
        if (!variable.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("invalid key environment-variable name");
        }
        String encoded = System.getenv(variable);
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("backup key environment variable is missing");
        }
        byte[] key = Base64.getDecoder().decode(encoded);
        if (key.length != 32) {
            Arrays.fill(key, (byte) 0);
            throw new IllegalArgumentException("backup key must decode to exactly 32 bytes");
        }
        return key;
    }

    private static void encrypt(Path input, Path output, byte[] key)
            throws IOException, GeneralSecurityException {
        byte[] noncePrefix = new byte[8];
        new SecureRandom().nextBytes(noncePrefix);
        try (var source = new BufferedInputStream(Files.newInputStream(input));
             var target = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(
                     output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)))) {
            target.writeInt(MAGIC.length);
            target.write(MAGIC);
            target.writeInt(CHUNK_SIZE);
            target.write(noncePrefix);
            byte[] buffer = new byte[CHUNK_SIZE];
            int index = 0;
            int length;
            while ((length = readChunk(source, buffer)) > 0) {
                Cipher cipher = cipher(Cipher.ENCRYPT_MODE, key, nonce(noncePrefix, index));
                cipher.updateAAD(aad(index, length));
                byte[] encrypted = cipher.doFinal(buffer, 0, length);
                target.writeInt(length);
                target.write(encrypted);
                Arrays.fill(encrypted, (byte) 0);
                index = Math.incrementExact(index);
            }
            Arrays.fill(buffer, (byte) 0);
            target.writeInt(0);
        }
    }

    private static void decrypt(Path input, Path output, byte[] key)
            throws IOException, GeneralSecurityException {
        try (var source = new DataInputStream(new BufferedInputStream(Files.newInputStream(input)));
             var target = new BufferedOutputStream(Files.newOutputStream(
                     output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
            int magicLength = source.readInt();
            if (magicLength < 1 || magicLength > 128) {
                throw new IOException("invalid encrypted backup header");
            }
            if (!Arrays.equals(MAGIC, source.readNBytes(magicLength))) {
                throw new IOException("unsupported encrypted backup format");
            }
            int chunkSize = source.readInt();
            if (chunkSize < 64 * 1024 || chunkSize > 16 * 1024 * 1024) {
                throw new IOException("invalid encrypted backup chunk size");
            }
            byte[] noncePrefix = source.readNBytes(8);
            if (noncePrefix.length != 8) {
                throw new EOFException("truncated encrypted backup header");
            }
            int index = 0;
            while (true) {
                int length = source.readInt();
                if (length == 0) {
                    break;
                }
                if (length < 0 || length > chunkSize) {
                    throw new IOException("invalid encrypted backup chunk length");
                }
                byte[] encrypted = source.readNBytes(Math.addExact(length, TAG_BITS / 8));
                if (encrypted.length != length + TAG_BITS / 8) {
                    throw new EOFException("truncated encrypted backup chunk");
                }
                Cipher cipher = cipher(Cipher.DECRYPT_MODE, key, nonce(noncePrefix, index));
                cipher.updateAAD(aad(index, length));
                try {
                    byte[] plain = cipher.doFinal(encrypted);
                    target.write(plain);
                    Arrays.fill(plain, (byte) 0);
                } catch (AEADBadTagException failure) {
                    throw new GeneralSecurityException("encrypted backup authentication failed", failure);
                } finally {
                    Arrays.fill(encrypted, (byte) 0);
                }
                index = Math.incrementExact(index);
            }
            if (source.read() != -1) {
                throw new IOException("encrypted backup has trailing data");
            }
        }
    }

    private static Cipher cipher(int mode, byte[] key, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
        return cipher;
    }

    private static byte[] nonce(byte[] prefix, int index) {
        byte[] nonce = Arrays.copyOf(prefix, 12);
        nonce[8] = (byte) (index >>> 24);
        nonce[9] = (byte) (index >>> 16);
        nonce[10] = (byte) (index >>> 8);
        nonce[11] = (byte) index;
        return nonce;
    }

    private static byte[] aad(int index, int length) {
        return (index + ":" + length).getBytes(StandardCharsets.US_ASCII);
    }

    private static int readChunk(BufferedInputStream input, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = input.read(buffer, offset, buffer.length - offset);
            if (read < 0) {
                break;
            }
            offset += read;
        }
        return offset;
    }
}
