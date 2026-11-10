package jp.co.tsubame.wholesale.batch;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import jp.co.tsubame.wholesale.batch.csv.AtomicCsvOutput;
import jp.co.tsubame.wholesale.batch.csv.CsvException;
import jp.co.tsubame.wholesale.batch.csv.CsvReader;
import jp.co.tsubame.wholesale.batch.csv.CsvRecord;
import jp.co.tsubame.wholesale.batch.csv.CsvWriter;
import jp.co.tsubame.wholesale.batch.csv.InputSnapshot;
import jp.co.tsubame.wholesale.batch.entity.BatchRow;
import jp.co.tsubame.wholesale.batch.entity.BatchRun;
import jp.co.tsubame.wholesale.batch.service.BatchQueryService;
import jp.co.tsubame.wholesale.batch.service.BatchRowService;
import jp.co.tsubame.wholesale.batch.service.BatchRunService;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;

/** Intentionally unproxied: all calls cross a service proxy before any mutation. */
public class BatchOrchestrator {
    public static final int MAX_DATA_ROWS = 1000000;
    private BatchRunService batchRunService;
    private BatchRowService batchRowService;
    private BatchQueryService batchQueryService;

    public void setBatchRunService(BatchRunService value) { batchRunService = value; }
    public void setBatchRowService(BatchRowService value) { batchRowService = value; }
    public void setBatchQueryService(BatchQueryService value) { batchQueryService = value; }

    public int execute(Arguments args, Actor actor, PrintStream out, PrintStream err) throws IOException, UsageException {
        actor.require("BATCH");
        if ("import-orders".equals(args.getCommand())) { actor.require("SALES", "MANAGER"); }
        if ("health".equals(args.getCommand())) {
            out.println(batchQueryService.getHealth(actor));
            return 0;
        }
        if (args.isExport()) { return export(args, actor, out, err); }
        if ("run-history".equals(args.getCommand())) { return history(args, actor, out, err); }
        if ("row-results".equals(args.getCommand())) { return rows(args, actor, out, err); }
        if (args.isImport()) {
            try (InputSnapshot snapshot = new InputSnapshot(args.path("file"))) {
                return job(args, actor, snapshot, out, err);
            }
        }
        return job(args, actor, null, out, err);
    }

    private int job(Arguments args, Actor actor, InputSnapshot input, PrintStream out, PrintStream err)
            throws IOException, UsageException {
        String fileHash = input == null ? null : input.getSha256();
        StartResult start = batchRunService.recordStart(args.value("run-key"), args.getCommand(),
                args.getInvocation(), args.fingerprint(fileHash), fileHash, actor);
        BatchRun run = start.getRun();
        if (!start.isCreated()) {
            out.println("replayed=true (stored outcomes; no business mutations repeated)");
            summary(run, out);
            return run.getExitCode();
        }
        int number = 0;
        boolean hasMore = false;
        try {
            if ("import-orders".equals(args.getCommand())) {
                Set<String> repeated = OrderCsv.repeatedKeys(input);
                try (OrderCsv groups = new OrderCsv(input.reader())) {
                    OrderGroup group;
                    while ((group = groups.read()) != null) {
                        number = group.getNumber();
                        if (repeated.contains(group.keyToken())) {
                            group.reject("orderImport.noncontiguous",
                                    "external_key occurs in nonconsecutive groups; every fragment was rejected");
                        }
                        process(run, new RowTask(group), actor);
                    }
                }
            } else if (input != null) {
                try (CsvReader reader = input.reader()) {
                    reader.requireHeader(ImportFields.header(args.getCommand()));
                    CsvRecord record;
                    while ((record = reader.read()) != null) {
                        if (number == MAX_DATA_ROWS) {
                            throw new CsvException(record.getLine(), "data row limit exceeded: " + MAX_DATA_ROWS);
                        }
                        number++;
                        process(run, new RowTask(args.getCommand(), number, record.getLine(), record.getFields()), actor);
                    }
                }
            } else {
                boolean allocation = "daily-allocation".equals(args.getCommand());
                Date date = args.date(allocation ? "through" : "period-end");
                List<Long> ids = allocation
                        ? batchQueryService.listAllocationCandidates(date, args.limit() + 1, actor)
                        : batchQueryService.listBillingCandidates(date, args.limit() + 1, actor);
                hasMore = ids.size() > args.limit();
                for (Long id : ids) {
                    if (number == args.limit()) { break; }
                    number++;
                    process(run, new RowTask(args.getCommand(), number, id, date, args.has("finalize")), actor);
                }
            }
            run = batchRunService.recordComplete(run.getId(), hasMore,
                    hasMore ? "Explicit candidate limit reached; backlog remains. Use another run key after review."
                            : "All input rows/candidates visited; see row-results for outcomes.", actor);
            summary(run, out);
            if (run.getExitCode() == 3) {
                err.println("PARTIAL: rejected rows or bounded backlog remain; inspect row-results / has_more.");
            }
            return run.getExitCode();
        } catch (CsvException ex) {
            RowTask malformed = new RowTask(args.getCommand(), number + 1, ex.getLine(),
                    Arrays.asList("CSV_STRUCTURE_FAILURE", fileHash == null ? "" : fileHash));
            try {
                batchRunService.recordRejected(run.getId(), malformed, "csv.structure", ex.getMessage(), actor);
                run = batchRunService.recordFailed(run.getId(), ex.getMessage(), 3, actor);
            } catch (RuntimeException persistenceFailure) {
                err.println("Unable to persist CSV failure. Run may remain RUNNING; inspect before retrying.");
                throw persistenceFailure;
            }
            err.println(ex.getMessage());
            summary(run, out);
            return 3;
        } catch (IOException ex) {
            fail(run, ex, actor, err);
            throw ex;
        } catch (RuntimeException ex) {
            fail(run, ex, actor, err);
            throw ex;
        }
    }

    private void process(BatchRun run, RowTask task, Actor actor) {
        try {
            batchRowService.executeRow(run.getId(), task, actor);
        } catch (BusinessException ex) {
            // executeRow has already rolled back before this independent REQUIRES_NEW call.
            if ("monthly-billing".equals(task.getCommand()) && "billing.noShipments".equals(ex.getCode())) {
                batchRunService.recordNoWork(run.getId(), task, actor);
                return;
            }
            if ("permission.denied".equals(ex.getCode()) || ex.getCode().startsWith("authentication.")
                    || ex.getCode().startsWith("batch.")) { throw ex; }
            batchRunService.recordRejected(run.getId(), task, ex.getCode(),
                    "line " + task.getSourceLine() + ": " + ex.getMessage(), actor);
        }
    }

    private void fail(BatchRun run, Exception failure, Actor actor, PrintStream err) {
        try {
            batchRunService.recordFailed(run.getId(),
                    "Infrastructure/authorization failure: " + failure.getClass().getSimpleName()
                    + ". Committed rows remain durable; inspect before choosing a new key.", 1, actor);
        } catch (RuntimeException recordingFailure) {
            failure.addSuppressed(recordingFailure);
            err.println("Failure could not be persisted; run may remain RUNNING. Do not assume completion.");
        }
    }

    private int export(Arguments args, Actor actor, PrintStream out, PrintStream err) throws IOException, UsageException {
        long after = args.afterId();
        int written = 0;
        boolean hasMore = false;
        try (AtomicCsvOutput output = new AtomicCsvOutput(args.path("output"))) {
            output.csv().write(BatchQueryService.exportHeader(args.getCommand()));
            while (written < args.limit()) {
                int take = Math.min(500, args.limit() - written);
                List<List<Object>> page = batchQueryService.listExport(args.getCommand(), after, take + 1,
                        args.has("from") ? args.date("from") : null, args.has("to") ? args.date("to") : null,
                        args.value("status"), actor);
                int count = Math.min(take, page.size());
                for (int i = 0; i < count; i++) {
                    List<Object> row = page.get(i);
                    output.csv().write(row);
                    after = ((Number) row.get(0)).longValue();
                    written++;
                }
                hasMore = page.size() > count;
                if (!hasMore) { break; }
            }
            output.commit();
        }
        out.println("exported=" + written + " output=" + args.path("output").toAbsolutePath().normalize());
        err.println("has_more=" + hasMore + " next_after_id=" + after);
        return 0;
    }

    private int history(Arguments args, Actor actor, PrintStream out, PrintStream err) throws IOException {
        List<BatchRun> runs = batchRunService.listRuns(args.afterId(), args.limit() + 1, actor);
        CsvWriter csv = new CsvWriter(new OutputStreamWriter(out, Charset.forName("UTF-8")));
        csv.write(Arrays.asList("id", "run_key", "command", "status", "total_rows", "success_rows", "rejected_rows",
                "has_more", "exit_code", "started_at", "ended_at", "actor", "input_sha256", "payload_hash", "message", "invocation"));
        int count = Math.min(args.limit(), runs.size());
        long after = args.afterId();
        for (int i = 0; i < count; i++) {
            BatchRun run = runs.get(i);
            csv.write(Arrays.<Object>asList(run.getId(), run.getRunKey(), run.getCommand(), run.getStatus(),
                    run.getTotalRows(), run.getSuccessRows(), run.getRejectedRows(), run.isHasMore(), run.getExitCode(),
                    timestamp(run.getStartedAt()), timestamp(run.getEndedAt()), run.getActorLogin(),
                    run.getInputSha256(), run.getPayloadHash(), run.getMessage(), run.getInvocation()));
            after = run.getId();
        }
        csv.flush();
        err.println("has_more=" + (runs.size() > count) + " next_after_id=" + after);
        return 0;
    }

    private int rows(Arguments args, Actor actor, PrintStream out, PrintStream err) throws IOException {
        BatchRun run = batchRunService.getRun(args.value("run-key"), actor);
        List<BatchRow> rows = batchRunService.listRows(run.getId(), args.afterRow(), args.limit() + 1, actor);
        CsvWriter csv = new CsvWriter(new OutputStreamWriter(out, Charset.forName("UTF-8")));
        csv.write(Arrays.asList("row_number", "source_line", "result", "code", "message", "entity_type",
                "entity_id", "entity_reference", "finished_at", "payload_hash"));
        int count = Math.min(args.limit(), rows.size());
        int after = args.afterRow();
        for (int i = 0; i < count; i++) {
            BatchRow row = rows.get(i);
            csv.write(Arrays.<Object>asList(row.getRowNumber(), row.getSourceLine(), row.getResult(), row.getCode(),
                    row.getMessage(), row.getEntityType(), row.getEntityId(), row.getEntityReference(),
                    timestamp(row.getFinishedAt()), row.getPayloadHash()));
            after = row.getRowNumber();
        }
        csv.flush();
        err.println("run_status=" + run.getStatus() + " has_more=" + (rows.size() > count) + " next_after_row=" + after);
        return 0;
    }

    public static void summary(BatchRun run, PrintStream out) {
        out.println("run_id=" + run.getId() + " run_key=" + run.getRunKey() + " status=" + run.getStatus()
                + " total=" + run.getTotalRows() + " success=" + run.getSuccessRows()
                + " rejected=" + run.getRejectedRows() + " has_more=" + run.isHasMore()
                + " exit_code=" + run.getExitCode());
    }

    private static String timestamp(Date date) {
        if (date == null) { return ""; }
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.ROOT);
        format.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Tokyo"));
        return format.format(date);
    }
}
