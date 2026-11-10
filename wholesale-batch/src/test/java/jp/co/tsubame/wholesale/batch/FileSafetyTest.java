package jp.co.tsubame.wholesale.batch;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;
import jp.co.tsubame.wholesale.batch.csv.AtomicCsvOutput;
import jp.co.tsubame.wholesale.batch.csv.CsvReader;
import jp.co.tsubame.wholesale.batch.csv.InputSnapshot;
import jp.co.tsubame.wholesale.common.Fingerprints;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class FileSafetyTest {
    private Path directory;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    @Before public void prepare() throws Exception {
        directory = Paths.get("target", "batch-files-" + UUID.randomUUID().toString());
        Files.createDirectories(directory);
    }

    @After public void cleanup() throws Exception {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) { Files.deleteIfExists(file); }
        }
        Files.delete(directory);
    }

    @Test public void destinationAppearsOnlyAfterSuccessfulCommit() throws Exception {
        Path output = directory.resolve("export.csv");
        try (AtomicCsvOutput file = new AtomicCsvOutput(output)) {
            file.csv().write(Arrays.asList("名前", "value"));
            assertFalse(Files.exists(output));
            file.commit();
        }
        assertEquals("名前,value\r\n", new String(Files.readAllBytes(output), UTF8));
        assertEquals(1, fileCount());
    }

    @Test public void abortRemovesStagingWithoutCreatingDestination() throws Exception {
        Path output = directory.resolve("aborted.csv");
        try (AtomicCsvOutput file = new AtomicCsvOutput(output)) {
            file.csv().write(Arrays.asList("some", "rows"));
        }
        assertFalse(Files.exists(output));
        assertEquals(0, fileCount());
    }

    @Test public void refusesExistingFileAndNeverDeletesIt() throws Exception {
        Path output = directory.resolve("existing.csv");
        Files.write(output, "keep".getBytes(UTF8));
        try {
            new AtomicCsvOutput(output);
            fail("Should not overwrite");
        } catch (IOException expected) { }
        assertEquals("keep", new String(Files.readAllBytes(output), UTF8));
    }

    @Test public void concurrentDestinationWinsWithoutBeingClobbered() throws Exception {
        Path output = directory.resolve("race.csv");
        try (AtomicCsvOutput file = new AtomicCsvOutput(output)) {
            file.csv().write(Arrays.asList("ours"));
            Files.write(output, "theirs".getBytes(UTF8));
            try { file.commit(); fail("Concurrent destination must not be overwritten"); }
            catch (IOException expected) { }
        }
        assertEquals("theirs", new String(Files.readAllBytes(output), UTF8));
        assertEquals(1, fileCount());
    }

    @Test public void snapshotHashesExactFileAndIgnoresSubsequentSourceChanges() throws Exception {
        Path input = directory.resolve("input.csv");
        byte[] bytes = "\ufeffa,b\r\nc,d".getBytes(UTF8);
        Files.write(input, bytes);
        java.security.MessageDigest digest = Fingerprints.sha256();
        digest.update(bytes);
        try (InputSnapshot snapshot = new InputSnapshot(input)) {
            assertEquals(Fingerprints.hex(digest.digest()), snapshot.getSha256());
            Files.write(input, "different".getBytes(UTF8));
            try (CsvReader reader = snapshot.reader()) {
                assertEquals(Arrays.asList("a", "b"), reader.read().getFields());
                assertEquals(Arrays.asList("c", "d"), reader.read().getFields());
            }
        }

    }

    @Test public void snapshotReadsReadOnlyInputWithoutCreatingSourceDirectoryFiles() throws Exception {
        Path input = directory.resolve("readonly.csv");
        Files.write(input, "code,name\nP001,example\n".getBytes(UTF8));
        assertTrue(input.toFile().setReadOnly());
        try {
            try (InputSnapshot snapshot = new InputSnapshot(input); CsvReader reader = snapshot.reader()) {
                assertEquals(Arrays.asList("code", "name"), reader.read().getFields());
                assertEquals(Arrays.asList("P001", "example"), reader.read().getFields());
                assertEquals(1, fileCount());
            }
            assertEquals(1, fileCount());
        } finally {
            assertTrue(input.toFile().setWritable(true));
        }
    }

    @Test(timeout = 30000)
    public void snapshotIsIndependentOfWorkingDirectory() throws Exception {
        Path input = directory.resolve("absolute-input.csv").toAbsolutePath();
        Files.write(input, "code,name\nP001,example\n".getBytes(UTF8));
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        StringBuilder absoluteClasspath = new StringBuilder();
        for (String entry : classpath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (absoluteClasspath.length() > 0) { absoluteClasspath.append(File.pathSeparator); }
            absoluteClasspath.append(new File(entry).getAbsolutePath());
        }
        File java = new File(new File(System.getProperty("java.home"), "bin"),
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        Process process = new ProcessBuilder(java.getAbsolutePath(),
                "-Duser.dir=" + directory.resolve("missing-working-directory").toAbsolutePath(),
                "-cp", absoluteClasspath.toString(), SnapshotProcess.class.getName(), input.toString())
                .redirectErrorStream(true).start();
        try {
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), UTF8))) {
                String line;
                while ((line = reader.readLine()) != null) { output.append(line).append('\n'); }
            }
            assertEquals(output.toString(), 0, process.waitFor());
            assertTrue(output.toString(), output.toString().contains("snapshot parsed"));
        } finally {
            process.destroy();
        }
    }

    public static class SnapshotProcess {
        public static void main(String[] args) throws Exception {
            try (InputSnapshot snapshot = new InputSnapshot(Paths.get(args[0]));
                 CsvReader reader = snapshot.reader()) {
                if (!Arrays.asList("code", "name").equals(reader.read().getFields())) {
                    throw new IllegalStateException("Snapshot contents changed");
                }
                System.out.println("snapshot parsed");
            }
        }
    }

    private int fileCount() throws Exception {
        int count = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path ignored : files) { count++; }
        }
        return count;
    }
}
