package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.util.Date;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.Invoice;
import jp.co.tsubame.wholesale.entity.PaymentReceipt;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReceivablesReportingTest {
    @Test
    public void ageingBoundariesAreInclusiveAndFutureDueAmountsAreCurrent() {
        Date asOf = Dates.parse("2026-09-08");
        ReceivablesAgeing result = new ReceivablesAgeing(new Customer(), asOf);
        for (int age : new int[] {-1, 0, 1, 30, 31, 60, 61, 90, 91}) {
            Invoice invoice = new Invoice();
            invoice.setId(Long.valueOf(age + 100));
            invoice.setNumber("INV-" + age);
            invoice.setDueDate(Dates.addDays(asOf, -age));
            result.add(new ReceivablesAgeingLine(invoice, asOf, new BigDecimal("100.00")));
        }
        assertEquals(new BigDecimal("200.00"), result.getCurrent());
        assertEquals(new BigDecimal("200.00"), result.getDays1To30());
        assertEquals(new BigDecimal("200.00"), result.getDays31To60());
        assertEquals(new BigDecimal("200.00"), result.getDays61To90());
        assertEquals(new BigDecimal("100.00"), result.getDaysOver90());
        assertEquals(new BigDecimal("900.00"), result.getTotalOutstanding());
        result.setUnallocatedReceipts(new BigDecimal("50.00"));
        result.setUnappliedCredits(new BigDecimal("25.00"));
        assertEquals(new BigDecimal("825.00"), result.getNetBalance());
    }

    @Test
    public void statementHasRunningBalancesWithoutTreatingAllocationAsNewCash() {
        Date date = Dates.parse("2026-09-08");
        AccountStatement result = new AccountStatement(new Customer(), date, date, new BigDecimal("100.00"));
        result.add(new AccountStatementLine(date, "INVOICE", 1L, "INV-1", "invoice",
                new BigDecimal("550.00"), Money.ZERO));
        result.add(new AccountStatementLine(date, "RECEIPT", 2L, "RCT-2", "bank",
                Money.ZERO, new BigDecimal("600.00")));
        result.add(new AccountStatementLine(date, "CREDIT", 3L, "CRM-3", "return",
                Money.ZERO, new BigDecimal("110.00")));
        assertEquals(new BigDecimal("650.00"), result.getLines().get(0).getBalance());
        assertEquals(new BigDecimal("50.00"), result.getLines().get(1).getBalance());
        assertEquals(new BigDecimal("-60.00"), result.getClosingBalance());
        assertEquals(new BigDecimal("550.00"), result.getDebitTotal());
        assertEquals(new BigDecimal("710.00"), result.getCreditTotal());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void statementConsumersCannotAppendUnreconciledLines() {
        AccountStatement result = new AccountStatement(new Customer(), Dates.today(), Dates.today(), Money.ZERO);
        result.getLines().add(null);
    }

    @Test
    public void invoiceOutstandingIsNonnegativeAndAccountsForCreditAndPayment() {
        Invoice invoice = new Invoice();
        invoice.setTotalAmount(new BigDecimal("110.00"));
        invoice.setPaidAmount(new BigDecimal("50.00"));
        invoice.setCreditedAmount(new BigDecimal("60.00"));
        assertEquals(Money.ZERO, invoice.getOutstandingAmount());
        invoice.setCreditedAmount(new BigDecimal("70.00"));
        assertEquals(Money.ZERO, invoice.getOutstandingAmount());
    }

    @Test
    public void cancelledReceiptNoLongerOffersUnallocatedFunds() {
        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setAmount(new BigDecimal("200.00"));
        receipt.setAllocatedAmount(new BigDecimal("50.00"));
        receipt.setStatus("POSTED");
        assertEquals(new BigDecimal("150.00"), receipt.getUnallocatedAmount());
        receipt.setAllocatedAmount(Money.ZERO);
        receipt.setStatus("CANCELLED");
        assertEquals(Money.ZERO, receipt.getUnallocatedAmount());
    }

    @Test
    public void cashReversalIsNegativeNetMovementNotNegativeReceipt() {
        BillingCashEntry movement = new BillingCashEntry(1L, "RCT-1", 2L, "試験先",
                Dates.today(), "RECEIPT_CANCEL", "CASH", "", Money.ZERO,
                new BigDecimal("100.00"), "billing", "誤記訂正");
        assertEquals(new BigDecimal("-100.00"), movement.getNetAmount());
        assertEquals(new BigDecimal("100.00"), movement.getOutflow());
    }
}
