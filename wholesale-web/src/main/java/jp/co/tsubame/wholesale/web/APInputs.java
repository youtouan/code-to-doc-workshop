package jp.co.tsubame.wholesale.web;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jp.co.tsubame.wholesale.common.APCreditInput;
import jp.co.tsubame.wholesale.common.APCreditLineInput;
import jp.co.tsubame.wholesale.common.APInvoiceInput;
import jp.co.tsubame.wholesale.common.APInvoiceLineInput;
import jp.co.tsubame.wholesale.common.APMatchInput;
import jp.co.tsubame.wholesale.common.APPaymentInput;
import jp.co.tsubame.wholesale.common.APPaymentLineInput;
import jp.co.tsubame.wholesale.common.APSearch;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.web.form.APCreditForm;
import jp.co.tsubame.wholesale.web.form.APForm;
import jp.co.tsubame.wholesale.web.form.APInvoiceForm;
import jp.co.tsubame.wholesale.web.form.APMatchForm;
import jp.co.tsubame.wholesale.web.form.APPaymentForm;

public final class APInputs {
    private APInputs() { }
    public static APSearch search(APForm form) {
        Search base = Inputs.search(form);
        APSearch search = new APSearch();
        search.setText(base.getText()); search.setStatus(base.getStatus());
        search.setFrom(base.getFrom()); search.setTo(base.getTo()); search.setPage(base.getPage()); search.setSize(base.getSize());
        search.setSupplierId(Inputs.optionalId(form.getSupplierId(), "仕入先"));
        return search;
    }
    public static APInvoiceInput invoice(APInvoiceForm form) {
        APInvoiceInput input = new APInvoiceInput();
        input.setSupplierId(Inputs.id(form.getSupplierId(), "仕入先"));
        input.setSupplierInvoiceNumber(Inputs.text(form.getSupplierInvoiceNumber(), "仕入先請求番号", 80, true));
        input.setInvoiceDate(Inputs.date(form.getInvoiceDate(), "仕入先請求日", false));
        input.setDueDate(Inputs.date(form.getDueDate(), "支払期日", true));
        input.setNotes(Inputs.text(form.getNote(), "備考", 1000, false));
        Inputs.arrays(200, form.getProductId(), form.getQuantity(), form.getDescription(), form.getUnitPrice(), form.getTaxRate());
        for (int i = 0; i < form.getProductId().length; i++) {
            if (empty(form.getProductId()[i]) && empty(form.getQuantity()[i])
                    && empty(form.getDescription()[i]) && empty(form.getUnitPrice()[i]) && empty(form.getTaxRate()[i])) { continue; }
            APInvoiceLineInput line = new APInvoiceLineInput();
            line.setProductId(Inputs.id(form.getProductId()[i], "請求商品"));
            line.setDescription(Inputs.text(form.getDescription()[i], "品名", 160, false));
            line.setQuantity(Inputs.integer(form.getQuantity()[i], "請求数量", 1, 1000000));
            line.setUnitPrice(Inputs.money(form.getUnitPrice()[i], "請求単価", false));
            line.setTaxRate(rate(form.getTaxRate()[i])); input.getLines().add(line);
        }
        if (input.getLines().isEmpty()) { throw Inputs.invalid("請求明細", "請求明細を1行以上入力してください。"); }
        return input;
    }
    public static BigDecimal rate(String value) {
        if (value == null || !value.matches("(0(\\.[0-9]{1,4})?|1(\\.0{1,4})?)")) {
            throw Inputs.invalid("税率", "税率は0～1の小数4桁以内で入力してください（10%は0.10）。");
        }
        return new BigDecimal(value);
    }
    public static List<APMatchInput> matches(APMatchForm form) {
        Inputs.arrays(500, form.getInvoiceLineId(), form.getReceiptLineId(), form.getQuantity());
        List<APMatchInput> result = new ArrayList<APMatchInput>();
        Set<String> pairs = new HashSet<String>();
        for (int i = 0; i < form.getInvoiceLineId().length; i++) {
            if (empty(form.getInvoiceLineId()[i]) && empty(form.getReceiptLineId()[i]) && empty(form.getQuantity()[i])) { continue; }
            int quantity = empty(form.getQuantity()[i]) ? 0 : Inputs.integer(form.getQuantity()[i], "照合数量", 0, 1000000);
            Long invoiceLine = Inputs.id(form.getInvoiceLineId()[i], "請求明細");
            Long receiptLine = Inputs.id(form.getReceiptLineId()[i], "入荷明細");
            if (quantity == 0) { continue; }
            if (!pairs.add(invoiceLine + ":" + receiptLine)) { throw Inputs.invalid("照合明細", "請求・入荷明細の組合せが重複しています。"); }
            APMatchInput line = new APMatchInput();
            line.setInvoiceLineId(invoiceLine); line.setReceiptLineId(receiptLine); line.setQuantity(quantity); result.add(line);
        }
        if (result.isEmpty()) { throw Inputs.invalid("照合明細", "照合数量を入力してください。全解除には専用ボタンを使用してください。"); }
        return result;
    }
    public static APCreditInput credit(APCreditForm form) {
        APCreditInput input = new APCreditInput();
        input.setSupplierCreditNumber(Inputs.text(form.getSupplierCreditNumber(), "仕入先値引番号", 80, true));
        input.setCreditDate(Inputs.date(form.getCreditDate(), "値引日", false));
        input.setReason(Inputs.text(form.getReason(), "財務値引理由", 500, true));
        Inputs.arrays(200, form.getInvoiceLineId(), form.getNetAmount()); Inputs.uniqueIds(form.getInvoiceLineId(), "請求明細");
        for (int i = 0; i < form.getInvoiceLineId().length; i++) {
            BigDecimal amount = Inputs.money(form.getNetAmount()[i], "税抜値引額", true);
            if (amount == null || amount.signum() == 0) { continue; }
            APCreditLineInput line = new APCreditLineInput();
            line.setInvoiceLineId(Inputs.id(form.getInvoiceLineId()[i], "請求明細")); line.setNetAmount(amount); input.getLines().add(line);
        }
        if (input.getLines().isEmpty()) { throw Inputs.invalid("値引明細", "税抜値引額を1行以上入力してください。"); }
        return input;
    }
    public static APPaymentInput payment(APPaymentForm form) {
        APPaymentInput input = new APPaymentInput();
        input.setRequestKey(Inputs.text(form.getRequestKey(), "受付キー", 100, true));
        input.setSupplierId(Inputs.id(form.getSupplierId(), "仕入先"));
        input.setPaymentDate(Inputs.date(form.getPaymentDate(), "支払日", false));
        input.setAmount(Inputs.money(form.getAmount(), "支払額", false));
        input.setMethod(Inputs.choice(form.getMethod(), "支払方法", "BANK_TRANSFER", "CASH", "CHEQUE"));
        input.setReference(Inputs.text(form.getReference(), "振込照合番号", 100, false));
        input.setNotes(Inputs.text(form.getNote(), "備考", 1000, false));
        Inputs.arrays(200, form.getInvoiceId(), form.getAllocationAmount());
        Set<Long> invoices = new HashSet<Long>();
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < form.getInvoiceId().length; i++) {
            if (empty(form.getInvoiceId()[i]) && empty(form.getAllocationAmount()[i])) { continue; }
            Long invoice = Inputs.id(form.getInvoiceId()[i], "支払先請求");
            BigDecimal amount = Inputs.money(form.getAllocationAmount()[i], "消込額", false);
            if (amount.signum() == 0 || !invoices.add(invoice)) { throw Inputs.invalid("消込明細", "正の金額を入力し、同じ請求の重複を除いてください。"); }
            APPaymentLineInput line = new APPaymentLineInput();
            line.setInvoiceId(invoice); line.setAmount(amount); input.getLines().add(line); sum = sum.add(amount);
        }
        if (input.getLines().isEmpty() || sum.compareTo(input.getAmount()) != 0) {
            throw Inputs.invalid("支払額", "支払額は1行以上の請求消込額の合計と一致させてください。");
        }
        return input;
    }
    private static boolean empty(String value) { return value == null || value.length() == 0; }
}
