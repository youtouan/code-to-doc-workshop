package jp.co.tsubame.wholesale.service;

import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.common.StockReconciliationRow;
import jp.co.tsubame.wholesale.common.StockValuationRow;
import jp.co.tsubame.wholesale.dao.StockControlReportDao;

public class StockControlReportService extends StockControlSupport {
    private StockControlReportDao reports;

    public void setReports(StockControlReportDao reports) { this.reports = reports; }

    public Page<StockReconciliationRow> listReconciliation(Actor actor, Search search) {
        reader(actor);
        return reports.reconciliation(search);
    }

    public Page<StockValuationRow> listValuation(Actor actor, Search search) {
        reader(actor);
        return reports.valuation(search);
    }
}
