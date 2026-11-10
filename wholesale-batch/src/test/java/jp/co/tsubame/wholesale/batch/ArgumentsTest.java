package jp.co.tsubame.wholesale.batch;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArgumentsTest {
    private static final String[] AUTH = {"--user", "batch", "--password-env", "TEST_SECRET"};

    @Test public void helpWorksWithoutDatabaseOrPassword() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        int code = BatchMain.run(new String[] {"help"}, new PrintStream(output, true, "UTF-8"),
                new PrintStream(error, true, "UTF-8"), Collections.<String, String>emptyMap());
        assertEquals(0, code);
        assertTrue(output.toString("UTF-8").contains("monthly-billing"));
        assertTrue(output.toString("UTF-8").contains("RUNNING"));
        assertEquals("", error.toString("UTF-8"));
    }

    @Test public void invalidUsageReturnsTwoBeforeDatabaseStartup() throws Exception {
        String[][] inputs = {{}, {"unknown"}, {"health"}, {"help", "--bad"},
                {"health", "--password", "do-not-display"}, {"health", "--user", "batch", "--user", "batch"},
                {"health", "--user", "batch", "--password-env", "NOT-A-VARIABLE"},
                {"daily-allocation", "--limit", "999999"}};
        for (String[] args : inputs) {
            ByteArrayOutputStream error = new ByteArrayOutputStream();
            int result = BatchMain.run(args, new PrintStream(new ByteArrayOutputStream()),
                    new PrintStream(error, true, "UTF-8"), Collections.<String, String>emptyMap());
            assertEquals(2, result);
            assertFalse(error.toString("UTF-8").contains("do-not-display"));
        }
    }

    @Test public void missingPasswordEnvironmentIsConfigurationError() throws Exception {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        assertEquals(2, BatchMain.run(withAuth("health"), new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(error, true, "UTF-8"), Collections.<String, String>emptyMap()));
        assertTrue(error.toString("UTF-8").contains("environment variable"));
    }

    @Test public void boundsAndDatesAreStrict() throws Exception {
        for (String[] args : new String[][] {
            {"daily-allocation", "--through", "2026-02-30", "--limit", "10", "--run-key", "x"},
            {"daily-allocation", "--through", "2026-02-01", "--limit", "0", "--run-key", "x"},
            {"daily-allocation", "--through", "2026-02-01", "--limit", "1001", "--run-key", "x"},
            {"run-history", "--limit", "2", "--after-id", "-1"},
            {"monthly-billing", "--period-end", "2026-02-11", "--limit", "1", "--run-key", "x"},
            {"monthly-billing", "--period-end", "2999-02-28", "--limit", "1", "--run-key", "x"},
            {"row-results", "--run-key", "a b", "--limit", "1"}
        }) {
            try { Arguments.parse(withAuth(args)); fail("Expected strict validation"); }
            catch (UsageException expected) { }
        }
    }

    @Test public void fingerprintBindsPayloadAndEffectiveParametersNotCredentials() throws Exception {
        Arguments first = Arguments.parse(withAuth("daily-allocation", "--through", "2026-08-31",
                "--limit", "10", "--run-key", "a"));
        Arguments same = Arguments.parse(withAuth("daily-allocation", "--run-key", "b",
                "--limit", "10", "--through", "2026-08-31"));
        Arguments different = Arguments.parse(withAuth("daily-allocation", "--through", "2026-08-31",
                "--limit", "11", "--run-key", "a"));
        assertEquals(first.fingerprint(null), same.fingerprint(null));
        assertFalse(first.fingerprint(null).equals(different.fingerprint(null)));
        assertFalse(first.fingerprint("aaa").equals(first.fingerprint("bbb")));
        assertTrue(first.getInvocation().contains("\"--password-env\" \"TEST_SECRET\""));
    }

    private static String[] withAuth(String... arguments) {
        String[] result = new String[arguments.length + AUTH.length];
        System.arraycopy(arguments, 0, result, 0, arguments.length);
        System.arraycopy(AUTH, 0, result, arguments.length, AUTH.length);
        return result;
    }
}
