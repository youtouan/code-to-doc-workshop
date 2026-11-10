package jp.co.tsubame.wholesale.common;

import java.util.Date;

public class ReportFilter extends Search {
    private Long productId;
    private Long supplierId;
    private boolean exceptionsOnly;

    public ReportFilter() {
        setFrom(Dates.addDays(Dates.today(), -30));
        setTo(Dates.today());
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public boolean isExceptionsOnly() {
        return exceptionsOnly;
    }

    public void setExceptionsOnly(boolean exceptionsOnly) {
        this.exceptionsOnly = exceptionsOnly;
    }

    public void validatePeriod() {
        validateRange();
        Checks.state(!getTo().after(Dates.today()), "report.future", "集計終了日は本日以前にしてください。");
    }

    public void validateBacklogPeriod() {
        validateRange();
        Checks.state(!getTo().after(Dates.addDays(Dates.today(), 366)), "report.future",
                "納期終了日は本日から366日以内にしてください。");
    }

    private void validateRange() {
        Date from = Checks.date(getFrom(), "開始日");
        Date to = Checks.date(getTo(), "終了日");
        Checks.state(!to.before(from), "report.period", "終了日は開始日以降にしてください。");
        Checks.state(!to.after(Dates.addDays(from, 366)), "report.periodLimit", "集計期間は367日以内にしてください。");
    }
}
