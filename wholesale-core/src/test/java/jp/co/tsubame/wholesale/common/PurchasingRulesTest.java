package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.util.Collections;
import jp.co.tsubame.wholesale.entity.Supplier;
import jp.co.tsubame.wholesale.entity.SupplierProduct;
import jp.co.tsubame.wholesale.entity.Product;
import org.junit.Test;
import static org.junit.Assert.*;

public class PurchasingRulesTest {
    @Test
    public void reorderRoundingHonorsMinimumAndPackWithoutFloatingPoint() {
        assertEquals(24, PurchasingRules.roundToPack(13, 12, 12));
        assertEquals(48, PurchasingRules.roundToPack(2, 48, 12));
        assertEquals(1000000, PurchasingRules.roundToPack(1000000, 1, 1));
    }

    @Test(expected = BusinessException.class)
    public void roundedQuantityCannotExceedLimit() {
        PurchasingRules.roundToPack(999999, 1, 24);
    }

    @Test(expected = BusinessException.class)
    public void quantityRoundingDoesNotOverflowLongInput() {
        PurchasingRules.roundToPack(Long.MAX_VALUE, 1, 1);
    }

    @Test
    public void costsUseDecimalArithmetic() {
        assertEquals(new BigDecimal("0.30"), PurchasingRules.lineAmount(new BigDecimal("0.10"), 3));
    }

    @Test(expected = BusinessException.class)
    public void lineAmountHasABusinessLimit() {
        PurchasingRules.lineAmount(new BigDecimal("999999999999.99"), 2);
    }

    @Test
    public void supplierAllowsZeroMinimumAmountAndImmediatePayment() {
        Supplier supplier = new Supplier();
        supplier.setCode("S001");
        supplier.setName("架空試験仕入先");
        supplier.setPaymentTermDays(0);
        supplier.setDefaultLeadTimeDays(0);
        PurchasingRules.supplier(supplier);
    }

    @Test(expected = BusinessException.class)
    public void supplierCannotPromiseNegativeLeadTime() {
        PurchasingRules.leadTime(-1);
    }

    @Test(expected = BusinessException.class)
    public void supplierMinimumMustBePurchasableInPacks() {
        SupplierProduct offer = new SupplierProduct();
        offer.setSupplier(new Supplier());
        offer.setProduct(new Product());
        offer.setValidFrom(Dates.parse("2026-01-01"));
        offer.setMinimumQuantity(13);
        offer.setOrderPackSize(12);
        offer.setUnitCost(BigDecimal.ONE);
        PurchasingRules.supplierProduct(offer);
    }

    @Test
    public void receiptFingerprintIgnoresVersionAndInputLineOrdering() {
        PurchasingReceiptCommand command = receipt();
        PurchasingReceiptLineCommand second = line(2L, 2, 0, "");
        command.getLines().add(second);
        String first = PurchasingRules.receiptHash(command);
        command.setExpectedVersion(999);
        Collections.reverse(command.getLines());
        assertEquals(first, PurchasingRules.receiptHash(command));
        assertEquals(64, first.length());
    }

    @Test
    public void receiptFingerprintDetectsChangedRejectionReasonAndQuantity() {
        PurchasingReceiptCommand command = receipt();
        String first = PurchasingRules.receiptHash(command);
        command.getLines().get(0).setAcceptedQuantity(4);
        assertFalse(first.equals(PurchasingRules.receiptHash(command)));
        command.getLines().get(0).setAcceptedQuantity(3);
        command.getLines().get(0).setRejectionReason("別の検品理由");
        assertFalse(first.equals(PurchasingRules.receiptHash(command)));
    }

    @Test
    public void fingerprintUsesLengthPrefixesInsteadOfAmbiguousDelimiters() {
        PurchasingReceiptCommand first = receipt();
        PurchasingReceiptCommand second = receipt();
        first.setSupplierDeliveryNumber("a;b");
        first.setNotes("c");
        second.setSupplierDeliveryNumber("a");
        second.setNotes("b;c");
        assertFalse(PurchasingRules.receiptHash(first).equals(PurchasingRules.receiptHash(second)));
    }

    @Test(expected = BusinessException.class)
    public void receiptRequiresPositiveDeliveredQuantity() {
        PurchasingReceiptCommand command = receipt();
        command.getLines().clear();
        command.getLines().add(line(1L, 0, 0, ""));
        PurchasingRules.receiptLines(command);
    }

    @Test(expected = BusinessException.class)
    public void receiptRejectsDuplicateOrderLines() {
        PurchasingReceiptCommand command = receipt();
        command.getLines().add(line(1L, 1, 0, ""));
        PurchasingRules.receiptLines(command);
    }

    @Test(expected = BusinessException.class)
    public void rejectedGoodsNeedAReason() {
        PurchasingReceiptCommand command = receipt();
        command.getLines().get(0).setRejectionReason(" ");
        PurchasingRules.receiptLines(command);
    }

    @Test(expected = BusinessException.class)
    public void negativeAcceptedQuantityIsRejected() {
        PurchasingReceiptCommand command = receipt();
        command.getLines().get(0).setAcceptedQuantity(-1);
        PurchasingRules.receiptLines(command);
    }

    private PurchasingReceiptCommand receipt() {
        PurchasingReceiptCommand command = new PurchasingReceiptCommand();
        command.setRequestKey("TEST-RECEIPT");
        command.setOrderId(100L);
        command.setReceiptDate(Dates.parse("2026-01-10"));
        command.getLines().add(line(1L, 3, 1, "架空破損検品"));
        return command;
    }

    private PurchasingReceiptLineCommand line(Long id, int accepted, int rejected, String reason) {
        PurchasingReceiptLineCommand line = new PurchasingReceiptLineCommand();
        line.setOrderLineId(id);
        line.setAcceptedQuantity(accepted);
        line.setRejectedQuantity(rejected);
        line.setRejectionReason(reason);
        return line;
    }
}
