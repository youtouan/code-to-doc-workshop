package jp.co.tsubame.wholesale.service;

import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BacklogRow;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.ReportFilter;
import jp.co.tsubame.wholesale.common.SalesSummaryRow;
import jp.co.tsubame.wholesale.common.StockActivityRow;
import jp.co.tsubame.wholesale.common.SupplierQualityRow;
import jp.co.tsubame.wholesale.dao.OperationsReportDao;

public class OperationsReportService extends BaseService {
    private OperationsReportDao reportDao;

    public void setReportDao(OperationsReportDao reportDao) {
        this.reportDao = reportDao;
    }

    public Page<SalesSummaryRow> searchSales(String dimension, ReportFilter filter, Actor actor) {
        require(actor, "SALES", "MANAGER", "BILLING");
        validate(filter);
        return reportDao.sales(dimension, filter);
    }

    public Page<BacklogRow> searchBacklog(ReportFilter filter, Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "MANAGER", "BATCH");
        Checks.state(filter != null, "report.filter", "集計条件を指定してください。");
        filter.validateBacklogPeriod();
        return reportDao.backlog(filter);
    }

    public Page<SupplierQualityRow> searchSupplierQuality(ReportFilter filter, Actor actor) {
        require(actor, "WAREHOUSE", "MANAGER", "BILLING");
        validate(filter);
        return reportDao.supplierQuality(filter);
    }

    public Page<StockActivityRow> searchStockActivity(ReportFilter filter, Actor actor) {
        require(actor, "WAREHOUSE", "MANAGER", "BILLING", "BATCH");
        validate(filter);
        return reportDao.stockActivity(filter);
    }

    private void validate(ReportFilter filter) {
        Checks.state(filter != null, "report.filter", "集計条件を指定してください。");
        filter.validatePeriod();
    }
}
