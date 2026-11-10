package jp.co.tsubame.wholesale.batch;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.AuthenticationResult;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.service.AuthService;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public final class BatchMain {
    private BatchMain() { }

    public static void main(String[] args) {
        try {
            System.exit(run(args, new PrintStream(System.out, true, "UTF-8"),
                    new PrintStream(System.err, true, "UTF-8"), System.getenv()));
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 is required by the Java platform", impossible);
        }
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, out, err, System.getenv());
    }

    /** Testable entry point: never calls System.exit or closes caller-owned streams. */
    public static int run(String[] args, PrintStream out, PrintStream err, Map<String, String> environment) {
        ClassPathXmlApplicationContext context = null;
        try {
            Arguments options = Arguments.parse(args);
            if ("help".equals(options.getCommand())) { Arguments.help(out); return 0; }
            String password = environment.get(options.value("password-env"));
            if (password == null || password.length() == 0) {
                throw new UsageException("Password environment variable is not set or is empty: " + options.value("password-env"));
            }
            // Do not enable SQL logging or print Spring connection properties/exception stacks.
            context = new ClassPathXmlApplicationContext("application-context.xml");
            AuthService auth = context.getBean("authService", AuthService.class);
            char[] secret = password.toCharArray();
            password = null;
            AuthenticationResult authenticated;
            try { authenticated = auth.authenticate(options.value("user"), secret); }
            finally { Arrays.fill(secret, '\0'); }
            if (!authenticated.isAuthenticated()) {
                err.println("Authentication failed: invalid credentials, locked or inactive account.");
                return 2;
            }
            Actor actor = authenticated.getActor();
            actor.require("BATCH");
            BatchOrchestrator orchestrator = context.getBean("batchOrchestrator", BatchOrchestrator.class);
            return orchestrator.execute(options, actor, out, err);
        } catch (UsageException ex) {
            err.println("Usage/configuration error: " + ex.getMessage());
            err.println("Use help for commands and exact flags.");
            return 2;
        } catch (BusinessException ex) {
            err.println(ex.getCode() + ": " + safe(ex.getMessage()));
            if ("batch.running".equals(ex.getCode())) { return 1; }
            if ("batch.keyConflict".equals(ex.getCode()) || "batch.notFound".equals(ex.getCode())
                    || "permission.denied".equals(ex.getCode()) || ex.getCode().startsWith("authentication.")) { return 2; }
            return 3;
        } catch (IOException ex) {
            err.println("File I/O failure (" + ex.getClass().getSimpleName() + "): " + safe(ex.getMessage()));
            return 1;
        } catch (RuntimeException ex) {
            Throwable cause = ex;
            int depth = 0;
            while (cause.getCause() != null && cause.getCause() != cause && depth++ < 20) { cause = cause.getCause(); }
            err.println("Database/runtime failure (" + cause.getClass().getSimpleName()
                    + "). Check database connectivity, applied SQL migrations, and runtime configuration.");
            err.println("No automatic retry was performed; inspect run-history before reusing a key.");
            return 1;
        } finally {
            if (context != null) {
                try { context.close(); }
                catch (RuntimeException ex) { err.println("Warning: application context shutdown failed (" + ex.getClass().getSimpleName() + ")."); }
            }
        }
    }

    private static String safe(String value) {
        if (value == null) { return ""; }
        String clean = value.replaceAll("[\\p{Cntrl}&&[^\\t]]", "?");
        return clean.length() > 2000 ? clean.substring(0, 2000) : clean;
    }
}
