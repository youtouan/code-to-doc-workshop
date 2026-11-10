package jp.co.tsubame.wholesale.common;

import java.util.Date;
import jp.co.tsubame.wholesale.entity.DeliveryAttempt;
import jp.co.tsubame.wholesale.entity.Shipment;

public final class DeliveryRules {
    private DeliveryRules() { }

    public static DeliveryAttempt observation(Shipment shipment, DeliveryAttemptCommand command) {
        Checks.state(command != null && command.getAttemptAt() != null, "delivery.attemptAt", "実際の配送試行日時が必要です。");
        Checks.state(java.util.Arrays.asList("DELIVERED","FAILED","RESCHEDULED").contains(command.getOutcome()),
                "delivery.outcome", "配送結果区分を指定してください。");
        Date at = new Date(command.getAttemptAt().getTime());
        Checks.state(shipment.getShippedDate() != null && !at.before(shipment.getShippedDate()) && !at.after(new Date()),
                "delivery.attemptAt", "配送試行日時は実出荷日以降かつ現在以前です。");
        DeliveryAttempt result = new DeliveryAttempt();
        result.setShipment(shipment);
        result.setAttemptAt(at);
        result.setBusinessDate(Dates.day(at));
        result.setOutcome(command.getOutcome());
        result.setReportingCompany(Checks.text(command.getReportingCompany(), "報告会社名（個人情報を含めない）", 120));
        result.setEvidenceReference(Checks.text(command.getEvidenceReference(), "手入力の報告参照番号", 120));
        result.setReason("DELIVERED".equals(command.getOutcome())
                ? Checks.optionalText(command.getReason(), "報告理由", 500)
                : Checks.text(command.getReason(), "未配達・再配送理由", 500));
        if ("RESCHEDULED".equals(command.getOutcome())) {
            Date next = Checks.date(command.getNextAttemptDate(), "次回配送予定日");
            Checks.state(next.after(result.getBusinessDate()) && !next.after(Dates.addDays(result.getBusinessDate(), 90)),
                    "delivery.nextDate", "次回配送日は試行翌日から90日後までです。");
            result.setNextAttemptDate(next);
        } else {
            Checks.state(command.getNextAttemptDate() == null, "delivery.nextDate", "再配送以外に次回予定日は指定できません。");
        }
        return result;
    }

    public static String fingerprint(DeliveryAttempt event) {
        return Fingerprints.of(String.valueOf(event.getShipment().getId()), event.getEventType(),
                String.valueOf(event.getSupersedesId()), String.valueOf(event.getAttemptAt().getTime()),
                event.getOutcome(), event.getReportingCompany(), event.getEvidenceReference(), event.getReason(),
                Dates.format(event.getNextAttemptDate()), event.getCorrectionReason());
    }

    public static void cursor(Long actual, Long expected) {
        Checks.state(actual == null ? expected == null : actual.equals(expected),
                "delivery.concurrent", "配送履歴が更新されました。最新履歴を再表示してください。");
    }
}
