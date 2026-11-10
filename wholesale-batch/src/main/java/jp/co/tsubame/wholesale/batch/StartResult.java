package jp.co.tsubame.wholesale.batch;

import jp.co.tsubame.wholesale.batch.entity.BatchRun;

public final class StartResult {
    private final BatchRun run;
    private final boolean created;

    public StartResult(BatchRun run, boolean created) { this.run = run; this.created = created; }
    public BatchRun getRun() { return run; }
    public boolean isCreated() { return created; }
}
