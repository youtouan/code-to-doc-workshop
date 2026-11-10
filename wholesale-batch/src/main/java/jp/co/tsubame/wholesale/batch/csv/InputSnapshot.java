package jp.co.tsubame.wholesale.batch.csv;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import jp.co.tsubame.wholesale.common.Fingerprints;

/** Copy once, hash the exact bytes subsequently parsed, and never retain uploaded contents. */
public final class InputSnapshot implements Closeable {
    public static final long MAX_BYTES = 268435456L;
    private final Path path;
    private String sha256;

    public InputSnapshot(Path source) throws IOException {
        path = Files.createTempFile(".wholesale-input-", ".csv");
        boolean complete = false;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(source); OutputStream out = Files.newOutputStream(path)) {
                byte[] buffer = new byte[16384];
                long total = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_BYTES) { throw new IOException("Input exceeds " + MAX_BYTES + " bytes"); }
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            sha256 = Fingerprints.hex(digest.digest());
            complete = true;
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        } finally {
            if (!complete) { Files.deleteIfExists(path); }
        }
    }

    public String getSha256() { return sha256; }
    public CsvReader reader() throws IOException { return new CsvReader(Files.newInputStream(path)); }
    public void close() throws IOException { Files.deleteIfExists(path); }
}
