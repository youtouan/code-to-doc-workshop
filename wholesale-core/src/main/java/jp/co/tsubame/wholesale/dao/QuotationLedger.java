package jp.co.tsubame.wholesale.dao;

import java.util.Date;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.QuotationRules;
import jp.co.tsubame.wholesale.entity.Quotation;
import jp.co.tsubame.wholesale.entity.QuotationEvent;

public final class QuotationLedger {
    private QuotationLedger() { }
    public static void event(WholesaleDao dao, Quotation quote, Actor actor,
            String operation, String previousState, String reason) {
        QuotationEvent event = new QuotationEvent();
        event.setQuotation(quote);
        event.setRevisionNumber(quote.getRevisionNumber());
        event.setOperation(operation);
        event.setFromStatus(previousState);
        event.setToStatus(quote.getStatus());
        event.setReason(Checks.optionalText(reason, "操作理由", 500));
        event.setFingerprint(quote.getCurrentRevision().getFingerprint());
        event.setActorId(QuotationRules.actorId(actor));
        event.setActor(actor.getLogin());
        event.setOccurredAt(new Date());
        dao.save(event);
    }
}
