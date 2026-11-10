package jp.co.tsubame.wholesale.batch.csv;

import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;

/** Publish a fully synced file atomically without clobbering an existing destination. */
public final class AtomicCsvOutput implements Closeable {
    private final Path destination;
    private final Path staging;
    private final FileOutputStream stream;
    private final CsvWriter csv;
    private boolean closed;

    public AtomicCsvOutput(Path destination) throws IOException {
        this.destination = destination.toAbsolutePath().normalize();
        if (Files.exists(this.destination, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new java.nio.file.FileAlreadyExistsException(this.destination.toString());
        }
        Path temporary = null;
        FileOutputStream opened = null;
        try {
            temporary = Files.createTempFile(this.destination.getParent(), ".wholesale-export-", ".part");
            opened = new FileOutputStream(temporary.toFile());
            staging = temporary;
            stream = opened;
            csv = new CsvWriter(new BufferedWriter(new OutputStreamWriter(stream,
                    Charset.forName("UTF-8").newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT))));
        } catch (IOException ex) {
            if (opened != null) { opened.close(); }
            if (temporary != null) { Files.deleteIfExists(temporary); }
            throw ex;
        }
    }

    public CsvWriter csv() { return csv; }

    public void commit() throws IOException {
        if (closed) { throw new IOException("Export already closed"); }
        csv.flush();
        stream.getFD().sync();
        csv.close();
        closed = true;
        // Java 7 ATOMIC_MOVE may replace existing files even without REPLACE_EXISTING.
        // An atomic same-filesystem link followed by unlink is the no-clobber move instead.
        // Unsupported filesystems fail closed; never fall back to a partial copy.
        Files.createLink(destination, staging);
        Files.delete(staging);
    }

    public void close() throws IOException {
        try {
            if (!closed) { closed = true; csv.close(); }
        } finally {
            Files.deleteIfExists(staging);
        }
    }
}
