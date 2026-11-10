package jp.co.tsubame.wholesale.batch.service;

import java.util.Date;
import java.util.List;
import jp.co.tsubame.wholesale.batch.RowOutcome;
import jp.co.tsubame.wholesale.batch.RowTask;
import jp.co.tsubame.wholesale.batch.StartResult;
import jp.co.tsubame.wholesale.batch.entity.BatchRow;
import jp.co.tsubame.wholesale.batch.entity.BatchRun;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.service.BaseService;

/** Every record* method is REQUIRES_NEW in the root XML transaction advice. */
public class BatchRunService extends BaseService {
    public StartResult recordStart(String key, String command, String invocation, String hash,
                                   String inputSha256, Actor actor) {
        require(actor, "BATCH");
        Checks.text(key, "run key", 120);
        dao.lockKey("batch-run", key);
        List<BatchRun> existing = dao.list("from BatchRun where runKey=:key", WholesaleDao.params("key", key));
        if (!existing.isEmpty()) {
            BatchRun run = existing.get(0);
            Checks.state(run.getActorId().equals(actor.getUserId()) || actor.hasRole("ADMIN"),
                    "permission.denied", "Run belongs to another account");
            Checks.state(hash.equals(run.getPayloadHash()) && command.equals(run.getCommand()),
                    "batch.keyConflict", "Run key already exists with a different payload or parameters");
            Checks.state(!"RUNNING".equals(run.getStatus()), "batch.running",
                    "Run is still RUNNING; automatic replay is unsafe. Inspect row-results and run-history; "
                    + "verify the original process has stopped before manual reconciliation.");
            return new StartResult(run, false);
        }
        BatchRun run = new BatchRun();
        run.setRunKey(key);
        run.setCommand(command);
        run.setInvocation(Checks.text(invocation, "invocation", 4000));
        run.setPayloadHash(hash);
        run.setInputSha256(inputSha256);
        run.setActorId(actor.getUserId());
        run.setActorLogin(actor.getLogin());
        run.setStatus("RUNNING");
        run.setStartedAt(new Date());
        run.setMessage("Started; only committed rows contribute to counts");
        dao.save(run);
        dao.flush();
        return new StartResult(run, true);
    }

    public BatchRun recordComplete(Long id, boolean hasMore, String message, Actor actor) {
        BatchRun run = lockRunning(id, actor);
        run.setHasMore(hasMore);
        run.setStatus(run.getRejectedRows() != 0 || hasMore ? "PARTIAL" : "COMPLETED");
        run.setExitCode("PARTIAL".equals(run.getStatus()) ? 3 : 0);
        run.setMessage(shortText(message, 2000));
        run.setEndedAt(new Date());
        dao.flush();
        return run;
    }

    public BatchRun recordFailed(Long id, String message, int exitCode, Actor actor) {
        BatchRun run = lockRunning(id, actor);
        run.setStatus("FAILED");
        run.setExitCode(exitCode);
        run.setMessage(shortText(message, 2000));
        run.setEndedAt(new Date());
        dao.flush();
        return run;
    }

    public BatchRow recordRejected(Long id, RowTask task, String code, String message, Actor actor) {
        BatchRun run = lockRunning(id, actor);
        BatchRow old = findRowInternal(id, task.getNumber());
        if (old != null) {
            Checks.state(old.getPayloadHash().equals(task.getHash()), "batch.rowConflict", "Row payload differs");
            // A lost commit acknowledgement can never convert a committed success into a rejection.
            return old;
        }
        BatchRow row = row(id, task, "REJECTED", code, message);
        if (task.getEntityId() != null) {
            row.setEntityId(task.getEntityId());
            row.setEntityType("daily-allocation".equals(task.getCommand()) ? "SalesOrder" : "Customer");
            row.setEntityReference(task.getEntityId().toString());
        }
        dao.save(row);
        run.setTotalRows(run.getTotalRows() + 1);
        run.setRejectedRows(run.getRejectedRows() + 1);
        dao.flush();
        return row;
    }

    public BatchRow recordNoWork(Long id, RowTask task, Actor actor) {
        Checks.state("monthly-billing".equals(task.getCommand()), "batch.commandConflict", "Only billing can have no work");
        BatchRun run = lockRunning(id, actor);
        BatchRow old = findRowInternal(id, task.getNumber());
        if (old != null) {
            Checks.state(old.getPayloadHash().equals(task.getHash()), "batch.rowConflict", "Row payload differs");
            return old;
        }
        return completeRow(run, task, new RowOutcome("billing.noShipments",
                "No unbilled shipments remain; customer was rechecked by BillingService (no mutation).",
                "Customer", task.getEntityId(), task.getEntityId().toString()), actor);
    }

    public BatchRun getRun(String key, Actor actor) {
        require(actor, "BATCH");
        List<BatchRun> rows = dao.list("from BatchRun where runKey=:key", WholesaleDao.params("key", key));
        if (rows.isEmpty()) { throw new BusinessException("batch.notFound", "No run with that key"); }
        return rows.get(0);
    }

    @SuppressWarnings("unchecked")
    public List<BatchRun> listRuns(long afterId, int limit, Actor actor) {
        require(actor, "BATCH");
        validateLimit(limit);
        return dao.query("from BatchRun where id>:after order by id", WholesaleDao.params("after", afterId))
                .setMaxResults(limit).list();
    }

    @SuppressWarnings("unchecked")
    public List<BatchRow> listRows(Long id, int afterRow, int limit, Actor actor) {
        require(actor, "BATCH");
        validateLimit(limit);
        return dao.query("from BatchRow where runId=:run and rowNumber>:after order by rowNumber",
                WholesaleDao.params("run", id, "after", afterRow)).setMaxResults(limit).list();
    }

    // These methods are invoked from the row worker in its existing transaction, not via record*.
    public BatchRun lockRunning(Long id, Actor actor) {
        require(actor, "BATCH");
        BatchRun run = dao.lock(BatchRun.class, id);
        Checks.state(run.getActorId().equals(actor.getUserId()) || actor.hasRole("ADMIN"),
                "permission.denied", "Run belongs to another account");
        Checks.state("RUNNING".equals(run.getStatus()), "batch.notRunning", "Run is no longer RUNNING");
        return run;
    }

    public BatchRow findRow(Long id, int row, Actor actor) {
        require(actor, "BATCH");
        return findRowInternal(id, row);
    }

    public BatchRow completeRow(BatchRun run, RowTask task, RowOutcome outcome, Actor actor) {
        require(actor, "BATCH");
        BatchRow row = row(run.getId(), task, "SUCCESS", outcome.getCode(), outcome.getMessage());
        row.setEntityType(outcome.getEntityType());
        row.setEntityId(outcome.getEntityId());
        row.setEntityReference(shortText(outcome.getReference(), 120));
        dao.save(row);
        run.setTotalRows(run.getTotalRows() + 1);
        run.setSuccessRows(run.getSuccessRows() + 1);
        dao.flush();
        return row;
    }

    private BatchRow findRowInternal(Long run, int number) {
        List<BatchRow> rows = dao.list("from BatchRow where runId=:run and rowNumber=:number",
                WholesaleDao.params("run", run, "number", number));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static BatchRow row(Long id, RowTask task, String result, String code, String message) {
        BatchRow row = new BatchRow();
        row.setRunId(id);
        row.setRowNumber(task.getNumber());
        row.setSourceLine(task.getSourceLine());
        row.setPayloadHash(task.getHash());
        row.setResult(result);
        row.setCode(shortText(code, 120));
        row.setMessage(shortText(task.sourceDescription() + message, 2000));
        row.setFinishedAt(new Date());
        return row;
    }

    private static void validateLimit(int limit) {
        Checks.state(limit > 0 && limit <= 1001, "batch.limit", "History page limit must be 1..1001");
    }

    public static String shortText(String value, int limit) {
        if (value == null) { return ""; }
        String clean = value.replace('\u0000', '?');
        if (clean.length() <= limit) { return clean; }
        int end = limit;
        if (Character.isHighSurrogate(clean.charAt(end - 1))) { end--; }
        return clean.substring(0, end);
    }
}
