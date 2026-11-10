package jp.co.tsubame.wholesale.web;

import java.util.Date;
import jp.co.tsubame.wholesale.common.DeliveryAttemptCommand;
import jp.co.tsubame.wholesale.common.DeliverySearch;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.web.form.DeliveryForm;

public final class DeliveryInputs {
    private DeliveryInputs() { }
    public static DeliveryAttemptCommand observation(DeliveryForm form) {
        DeliveryAttemptCommand command = new DeliveryAttemptCommand();
        command.setShipmentId(Inputs.id(form.getShipmentId(), "出荷"));
        command.setExpectedLatestEventId(Inputs.optionalId(form.getExpectedLatestEventId(), "最新記録ID"));
        command.setRequestKey(Inputs.text(form.getRequestKey(), "処理キー", 120, true));
        command.setAttemptAt(PreciseDates.parse(form.getAttemptAt(), "配送試行日時", false));
        command.setOutcome(Inputs.choice(form.getOutcome(), "手動報告結果", "DELIVERED", "FAILED", "RESCHEDULED"));
        command.setReportingCompany(Inputs.text(form.getReportingCompany(), "報告会社名", 120, true));
        command.setEvidenceReference(Inputs.text(form.getEvidenceReference(), "手入力の報告参照番号", 120, true));
        command.setReason(Inputs.text(form.getReason(), "報告理由", 500, !"DELIVERED".equals(form.getOutcome())));
        command.setNextAttemptDate(Inputs.date(form.getNextAttemptDate(), "次回配送予定日", !"RESCHEDULED".equals(form.getOutcome())));
        if (!"RESCHEDULED".equals(form.getOutcome()) && command.getNextAttemptDate() != null) {
            throw Inputs.invalid("次回配送予定日", "再配送以外の場合は次回予定日を空欄にしてください。");
        }
        return command;
    }
    public static DeliverySearch search(DeliveryForm form) {
        Search base = Inputs.search(form); DeliverySearch search = new DeliverySearch();
        search.setText(base.getText()); search.setStatus(base.getStatus()); search.setCustomerId(base.getCustomerId());
        search.setWarehouseId(base.getWarehouseId()); search.setFrom(base.getFrom()); search.setTo(base.getTo());
        search.setPage(base.getPage()); search.setSize(base.getSize());
        search.setShipmentId(Inputs.optionalId(form.getShipmentId(), "出荷"));
        search.setAsOfRecordedAt(cutoff(form)); search.setDueOnOrBefore(Inputs.date(form.getDueOnOrBefore(), "対応期限", true));
        return search;
    }
    public static Date cutoff(DeliveryForm form) {
        Date date = PreciseDates.parse(form.getAsOfRecordedAt(), "記録締切日時", true);
        if (date != null && date.after(new Date())) { throw Inputs.invalid("記録締切日時", "記録締切は現在以前の日時で指定してください。"); }
        return date;
    }
}
