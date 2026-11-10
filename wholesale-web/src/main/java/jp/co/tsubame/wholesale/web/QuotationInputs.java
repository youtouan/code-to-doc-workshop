package jp.co.tsubame.wholesale.web;

import java.util.HashSet;
import java.util.Set;
import jp.co.tsubame.wholesale.common.OrderAmendmentCommand;
import jp.co.tsubame.wholesale.common.OrderAmendmentLineCommand;
import jp.co.tsubame.wholesale.common.QuotationCommand;
import jp.co.tsubame.wholesale.common.QuotationLineCommand;
import jp.co.tsubame.wholesale.web.form.OrderAmendmentForm;
import jp.co.tsubame.wholesale.web.form.QuotationForm;

public final class QuotationInputs {
    private QuotationInputs() { }
    public static QuotationCommand quotation(QuotationForm form) {
        QuotationCommand command = new QuotationCommand();
        command.setCustomerId(Inputs.id(form.getCustomerId(), "得意先")); command.setWarehouseId(Inputs.id(form.getWarehouseId(), "倉庫"));
        command.setQuoteDate(Inputs.date(form.getQuoteDate(), "見積日", false));
        command.setValidUntil(Inputs.date(form.getValidUntil(), "有効期限", false));
        command.setRequestedDate(Inputs.date(form.getRequestedDate(), "納期", false));
        command.setDeliveryAddress(Inputs.text(form.getDeliveryAddress(), "納入先", 300, false));
        command.setExternalReference(Inputs.text(form.getExternalReference(), "客先参照番号", 80, false));
        command.setNotes(Inputs.text(form.getNotes(), "備考", 1000, false));
        Inputs.arrays(200, form.getProductId(), form.getQuantity(), form.getNegotiatedUnitPrice(), form.getNegotiationReason());
        Set<Long> products = new HashSet<Long>();
        for (int i = 0; i < form.getProductId().length; i++) {
            if (empty(form.getProductId()[i]) && empty(form.getQuantity()[i])
                    && empty(form.getNegotiatedUnitPrice()[i]) && empty(form.getNegotiationReason()[i])) { continue; }
            QuotationLineCommand line = new QuotationLineCommand();
            Long product = Inputs.id(form.getProductId()[i], "見積商品");
            if (!products.add(product)) { throw Inputs.invalid("見積商品", "同じ商品を重複せず指定してください。"); }
            line.setProductId(product); line.setQuantity(Inputs.integer(form.getQuantity()[i], "見積数量", 1, 1000000));
            line.setNegotiatedUnitPrice(Inputs.money(form.getNegotiatedUnitPrice()[i], "交渉提案単価", true));
            line.setNegotiationReason(Inputs.text(form.getNegotiationReason()[i], "交渉理由", 300, line.getNegotiatedUnitPrice() != null));
            command.getLines().add(line);
        }
        if (command.getLines().isEmpty()) { throw Inputs.invalid("見積明細", "見積明細を1行以上入力してください。"); }
        return command;
    }
    public static OrderAmendmentCommand amendment(OrderAmendmentForm form) {
        OrderAmendmentCommand command = new OrderAmendmentCommand();
        command.setOrderId(Inputs.id(form.getOrderId(), "受注"));
        command.setExpectedOrderVersion(Inputs.integer(form.getExpectedOrderVersion(), "元受注更新番号", 0, Integer.MAX_VALUE));
        command.setRequestedDate(Inputs.date(form.getRequestedDate(), "変更後納期", true));
        command.setReason(Inputs.text(form.getReason(), "変更理由", 500, true));
        if (form.getOrderLineId() != null && form.getTargetQuantity() != null
                && form.getOrderLineId().length == 0 && form.getTargetQuantity().length == 0 && command.getRequestedDate() != null) {
            return command;
        }
        Inputs.arrays(200, form.getOrderLineId(), form.getTargetQuantity()); Inputs.uniqueIds(form.getOrderLineId(), "受注明細");
        for (int i = 0; i < form.getOrderLineId().length; i++) {
            if (empty(form.getTargetQuantity()[i])) { continue; }
            OrderAmendmentLineCommand line = new OrderAmendmentLineCommand();
            line.setOrderLineId(Inputs.id(form.getOrderLineId()[i], "受注明細"));
            line.setTargetQuantity(Inputs.integer(form.getTargetQuantity()[i], "変更後総数量", 1, 1000000));
            command.getLines().add(line);
        }
        if (command.getRequestedDate() == null && command.getLines().isEmpty()) {
            throw Inputs.invalid("変更内容", "数量または納期の変更を入力してください。");
        }
        return command;
    }
    private static boolean empty(String value) { return value == null || value.length() == 0; }
}
