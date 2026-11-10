package jp.co.tsubame.wholesale.batch;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Fingerprints;

/** Parse and validate everything that can be checked without opening the database. */
public final class Arguments {
    private static final Set<String> JOBS = set("import-products", "import-receipts", "import-orders", "daily-allocation", "monthly-billing");
    private static final Set<String> EXPORTS = ExportDefinition.names();
    private final String command;
    private final Map<String, String> options = new LinkedHashMap<String, String>();
    private String invocation;

    private Arguments(String command) { this.command = command; }

    public static Arguments parse(String[] args) throws UsageException {
        if (args == null || args.length == 0) { throw new UsageException("Missing command; use help"); }
        String command = "--help".equals(args[0]) || "-h".equals(args[0]) ? "help" : args[0];
        Arguments parsed = new Arguments(command);
        if ("help".equals(command)) {
            if (args.length != 1) { throw new UsageException("help takes no flags"); }
            return parsed;
        }
        Set<String> allowed = set("user", "password-env");
        if (JOBS.contains(command)) {
            allowed.add("run-key");
            if (command.startsWith("import-")) { allowed.add("file"); }
            else {
                allowed.add("limit");
                allowed.add("daily-allocation".equals(command) ? "through" : "period-end");
                if ("monthly-billing".equals(command)) { allowed.add("finalize"); }
            }
        } else if (EXPORTS.contains(command)) {
            allowed.addAll(set("output", "limit", "after-id"));
            ExportDefinition definition = ExportDefinition.get(command);
            if (definition.supportsDate()) { allowed.addAll(set("from", "to")); }
            if (!definition.getStatuses().isEmpty()) { allowed.add("status"); }
        } else if ("run-history".equals(command)) {
            allowed.addAll(set("limit", "after-id"));
        } else if ("row-results".equals(command)) {
            allowed.addAll(set("run-key", "limit", "after-row"));
        } else if (!"health".equals(command)) {
            throw new UsageException("Unknown command: " + printable(command));
        }
        for (int i = 1; i < args.length; i++) {
            String flag = args[i];
            if (!flag.startsWith("--") || !allowed.contains(flag.substring(2))) {
                throw new UsageException("Unknown flag for " + command + ": " + printable(flag.split("=", 2)[0]));
            }
            String key = flag.substring(2);
            if (parsed.options.containsKey(key)) { throw new UsageException("Duplicate flag: " + flag); }
            String value = "true";
            if (!"finalize".equals(key)) {
                if (++i >= args.length || args[i].startsWith("--") || args[i].length() == 0) {
                    throw new UsageException("Missing value for " + flag);
                }
                value = args[i];
            }
            if (containsControl(value)) { throw new UsageException("Control characters are not allowed in " + flag); }
            parsed.options.put(key, value);
        }
        parsed.required("user");
        parsed.required("password-env");
        if (!parsed.value("user").matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,49}")) {
            throw new UsageException("--user must be a login identifier (maximum 50 characters)");
        }
        if (!parsed.value("password-env").matches("[A-Za-z_][A-Za-z0-9_]{0,127}")) {
            throw new UsageException("--password-env must name an environment variable, not contain a password");
        }
        if (JOBS.contains(command) || "row-results".equals(command)) {
            parsed.required("run-key");
            if (!parsed.value("run-key").matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,119}")) {
                throw new UsageException("--run-key must contain 1..120 letters, digits, dots, colons, hyphens or underscores");
            }
        }
        try {
            if (command.startsWith("import-")) {
                parsed.required("file");
                if (!Files.isRegularFile(parsed.path("file")) || !Files.isReadable(parsed.path("file"))) {
                    throw new UsageException("--file must be a readable regular file");
                }
            }
            if (EXPORTS.contains(command)) {
                parsed.required("output");
                Path output = parsed.path("output").toAbsolutePath().normalize();
                if (Files.exists(output, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    throw new UsageException("--output already exists; files are never overwritten");
                }
                if (output.getParent() == null || !Files.isDirectory(output.getParent())
                        || !Files.isWritable(output.getParent())) {
                    throw new UsageException("--output parent must be an existing writable directory");
                }
            }
        } catch (InvalidPathException ex) {
            throw new UsageException("Invalid file path");
        }
        if (allowed.contains("limit")) {
            parsed.required("limit");
            parsed.integer("limit", 1, EXPORTS.contains(command) ? 100000 : 1000);
        }
        if (parsed.options.containsKey("after-id")) { parsed.longNumber("after-id", 0, Long.MAX_VALUE); }
        if (parsed.options.containsKey("after-row")) { parsed.integer("after-row", 0, Integer.MAX_VALUE); }
        if (parsed.has("from")) { parsed.date("from"); }
        if (parsed.has("to")) { parsed.date("to"); }
        if (parsed.has("from") && parsed.has("to") && parsed.date("from").after(parsed.date("to"))) {
            throw new UsageException("--from must not be after --to");
        }
        if (parsed.has("status") && !ExportDefinition.get(command).getStatuses().contains(parsed.value("status"))) {
            throw new UsageException("--status must be one of " + ExportDefinition.get(command).getStatuses());
        }
        if ("daily-allocation".equals(command)) { parsed.required("through"); parsed.date("through"); }
        if ("monthly-billing".equals(command)) {
            parsed.required("period-end");
            Date end = parsed.date("period-end");
            if (end.after(Dates.today())) { throw new UsageException("--period-end cannot be in the future"); }
            int day = Dates.calendar(end).get(java.util.Calendar.DAY_OF_MONTH);
            if (day != 10 && day != 20 && !Dates.sameDay(end, Dates.monthEnd(end))) {
                throw new UsageException("--period-end must be the 10th, 20th, or last day of a month");
            }
        }
        StringBuilder invocation = new StringBuilder();
        for (String arg : args) {
            if (invocation.length() != 0) { invocation.append(' '); }
            invocation.append('"').append(arg.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        if (invocation.length() > 4000) { throw new UsageException("Command invocation exceeds 4000 characters"); }
        parsed.invocation = invocation.toString();
        return parsed;
    }

    private void required(String key) throws UsageException {
        if (!options.containsKey(key)) { throw new UsageException("Required flag: --" + key); }
    }

    public int integer(String key, int min, int max) throws UsageException {
        return (int) longNumber(key, min, max);
    }

    private long longNumber(String key, long min, long max) throws UsageException {
        try {
            String value = value(key);
            if (value == null || !value.matches("[0-9]+")) { throw new NumberFormatException(); }
            long number = Long.parseLong(value);
            if (number < min || number > max) { throw new NumberFormatException(); }
            return number;
        } catch (NumberFormatException ex) {
            throw new UsageException("--" + key + " must be an integer in " + min + ".." + max);
        }
    }

    public Date date(String key) throws UsageException {
        try { return Dates.parse(value(key)); }
        catch (BusinessException ex) { throw new UsageException("--" + key + " must be a real date in yyyy-MM-dd format"); }
    }

    public String fingerprint(String inputHash) {
        return Fingerprints.of("batch-v1", command, inputHash, value("through"), value("period-end"),
                has("limit") ? Integer.toString(limit()) : null, Boolean.toString(has("finalize")));
    }

    public String getCommand() { return command; }
    public boolean isJob() { return JOBS.contains(command); }
    public boolean isExport() { return EXPORTS.contains(command); }
    public boolean isImport() { return command.startsWith("import-"); }
    public String value(String key) { return options.get(key); }
    public boolean has(String key) { return options.containsKey(key); }
    public Path path(String key) { return Paths.get(value(key)); }
    public String getInvocation() { return invocation; }
    public int limit() { return Integer.parseInt(value("limit")); }
    public long afterId() { return has("after-id") ? Long.parseLong(value("after-id")) : 0L; }
    public int afterRow() { return has("after-row") ? Integer.parseInt(value("after-row")) : 0; }

    private static Set<String> set(String... values) { return new LinkedHashSet<String>(Arrays.asList(values)); }
    private static boolean containsControl(String value) {
        for (int i = 0; i < value.length(); i++) { if (Character.isISOControl(value.charAt(i))) { return true; } }
        return false;
    }
    private static String printable(String value) { return value.replaceAll("[\\p{Cntrl}]", "?"); }

    public static void help(PrintStream out) {
        out.println("Tsubame Wholesale persistent batch CLI (Java 8 runtime, Java 7 APIs)");
        out.println("java [DB JVM properties] -jar wholesale-batch-1.0.0-standalone.jar COMMAND FLAGS");
        out.println("DB properties: -Ddb.url=jdbc:postgresql://HOST:PORT/DB -Ddb.username=USER -Ddb.password=VALUE");
        out.println("Every command except help requires: --user LOGIN --password-env ENVIRONMENT_VARIABLE");
        out.println("  help");
        out.println("  health");
        out.println("  import-products --file PATH --run-key KEY");
        out.println("  import-receipts --file PATH --run-key KEY");
        out.println("  import-orders --file PATH --run-key KEY");
        out.println("  daily-allocation --through yyyy-MM-dd --limit 1..1000 --run-key KEY");
        out.println("  monthly-billing --period-end yyyy-MM-dd --limit 1..1000 --run-key KEY [--finalize]");
        out.println("  export-stock --output NEW.csv --limit 1..100000 [--after-id ID]");
        out.println("  export-orders --output NEW.csv --limit 1..100000 [--after-id ID]");
        out.println("  export-invoices --output NEW.csv --limit 1..100000 [--after-id ID]");
        out.println("  export-shipments --output NEW.csv --limit 1..100000 [--after-id LINE_ID]");
        out.println("  export-receipts --output NEW.csv --limit 1..100000 [--after-id ID]");
        out.println("  export-order-lines --output NEW.csv --limit 1..100000 [--after-id LINE_ID]");
        out.println("All exports except stock accept [--from yyyy-MM-dd] [--to yyyy-MM-dd] (inclusive).");
        out.println("Orders/order-lines date=order_date; invoices=period_end; receipts=receipt_date;");
        out.println("shipments date=shipped_date when confirmed, otherwise planned_date. Preserve filters when continuing cursors.");
        out.println("Orders/order-lines/invoices/shipments also accept --status; invalid statuses list allowed values.");
        out.println("  run-history --limit 1..1000 [--after-id ID]");
        out.println("  row-results --run-key KEY --limit 1..1000 [--after-row N]");
        out.println("Product CSV header: " + join(ImportFields.PRODUCTS));
        out.println("Receipt CSV header: " + join(ImportFields.RECEIPTS));
        out.println("Order CSV header: " + join(ImportFields.ORDERS));
        out.println("Orders: consecutive external_key rows form one DRAFT; header fields (including notes) must match.");
        out.println("All fragments of a nonconsecutive repeated external_key are rejected; preflight completes before mutations.");
        out.println("Order limits: 200 distinct products/order, 2 MiB decoded group, 100,000 groups/file; existing file/row limits also apply.");
        out.println("Order journal counts are GROUPS, not CSV lines; messages retain source-row and physical-line spans.");
        out.println("external_key uses the same identifier characters as run-key. Claim payload trims fields, normalizes quantities");
        out.println("and ignores product-line ordering; identical claims return the original order, changed payloads are rejected.");
        out.println("No price overrides or automatic submission/approval: core catalog pricing, tax, packs, dates and holds apply.");
        out.println("Order import needs ADMIN or both BATCH and SALES/MANAGER roles; plain batch account is explicitly refused.");
        out.println("CSV: strict UTF-8 (optional BOM), exact ordered header, RFC4180 quotes/newlines;");
        out.println("maximum input 256 MiB, 1,000,000 data rows, 65,536 field chars, 1,048,576 record chars.");
        out.println("Boolean values: true or false. Money: decimal without grouping. Dates: yyyy-MM-dd.");
        out.println("Imports replace the listed product fields only. Receipts use their own request_key.");
        out.println("Every row commits independently; business rejections are recorded after rollback.");
        out.println("Run key binds command/parameters and whole-file SHA-256; paths and credentials are excluded.");
        out.println("Identical terminal runs return stored outcomes without repeating mutations (even FAILED/PARTIAL).");
        out.println("RUNNING never auto-completes or auto-resumes. After a crash, inspect durable row-results,");
        out.println("verify the original process has stopped, and reconcile before choosing a NEW run key.");
        out.println("Input snapshots use java.io.tmpdir; export staging stays beside its destination for atomic publication.");
        out.println("After a hard crash, remove orphan .wholesale-input-* / .wholesale-export-* files only after stopping that process.");
        out.println("Do not blindly rerun under a new key: successful product/allocation/billing operations may differ.");
        out.println("Limits intentionally bound job candidates; has_more=true means PARTIAL (exit 3).");
        out.println("Allocation shortages are SUCCESS with explicit shortage quantities; never retried in a tight loop.");
        out.println("Billing without --finalize prepares drafts using the same service as the web; no-work is recorded.");
        out.println("Exports are ID-keyset paged, not a whole-database snapshot; has_more and next_after_id go to stderr.");
        out.println("Export text is spreadsheet-formula escaped; numeric amounts remain numeric. Existing files refused.");
        out.println("Publication uses an atomic same-directory hard-link/unlink move (NTFS/ext4); unsupported filesystems fail closed.");
        out.println("Only authenticated BATCH/ADMIN accounts may use this CLI; core services enforce their own roles.");
        out.println("Exit codes: 0 completed, 2 invalid usage/config/auth, 3 rejected data/partial, 1 infrastructure/FAILED.");
        out.println("Examples (password value belongs only in the named environment variable):");
        out.println("  import-products --file samples\\products-valid.csv --run-key products-001 --user batch --password-env WHOLESALE_BATCH_PASSWORD");
        out.println("  daily-allocation --through 2026-09-08 --limit 100 --run-key allocation-20260908 --user batch --password-env WHOLESALE_BATCH_PASSWORD");
    }

    private static String join(String[] fields) {
        StringBuilder text = new StringBuilder();
        for (String field : fields) {
            if (text.length() != 0) { text.append(','); }
            text.append(field);
        }
        return text.toString();
    }
}
