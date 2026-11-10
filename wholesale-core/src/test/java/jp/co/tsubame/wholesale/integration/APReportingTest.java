package jp.co.tsubame.wholesale.integration;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import javax.sql.DataSource;
import jp.co.tsubame.wholesale.common.APPaymentInput;
import jp.co.tsubame.wholesale.common.APSearch;
import jp.co.tsubame.wholesale.common.APStatement;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.entity.APCredit;
import jp.co.tsubame.wholesale.entity.APInvoice;
import jp.co.tsubame.wholesale.entity.APPaymentVoucher;
import org.junit.Test;
import static org.junit.Assert.*;

public class APReportingTest extends APFixtureTest {
    @Test
    public void reversalUsesItsOwnDateAndHistoricalStatementsIgnoreCurrentProjections() {
        APFixture fixture = apFixture(10);
        APInvoice invoice = matched(fixture, fixture.receive(10, 0), 10, "60");
        invoice = invoices().postInvoice(invoice.getId(), invoice.getVersion(), Dates.addDays(Dates.today(), -2), billingActor);
        APPaymentInput input = paymentInput(invoice, "200");
        input.setPaymentDate(Dates.addDays(Dates.today(), -1));
        APPaymentVoucher voucher = settlements().pay(input, billingActor);
        settlements().cancelPayment(voucher.getId(), voucher.getVersion(), "銀行照合による支払取消", billingActor);
        Date start = Dates.addDays(Dates.today(), -2);
        Date yesterday = Dates.addDays(Dates.today(), -1);
        APStatement historical = reports().getStatement(fixture.supplier.getId(), start, yesterday, 1, 25, billingActor);
        money("460", historical.getClosingBalance());
        money("660", historical.getCharges());
        money("200", historical.getReductions());
        money("460", reports().searchOpenItems(search(fixture.supplier), yesterday, false, billingActor)
                .getItems().get(0).getOutstandingAmount());
        APStatement today = reports().getStatement(fixture.supplier.getId(), Dates.today(), Dates.today(), 1, 25, billingActor);
        money("460", today.getOpeningBalance());
        money("200", today.getCharges());
        money("660", today.getClosingBalance());
        assertEquals("PAYMENT_CANCEL", today.getEntries().getItems().get(0).getType());
        APStatement secondPage = reports().getStatement(fixture.supplier.getId(), start, Dates.today(), 2, 1, billingActor);
        assertEquals(3, secondPage.getEntries().getTotal());
        assertEquals("PAYMENT", secondPage.getEntries().getItems().get(0).getType());
        money("460", secondPage.getEntries().getItems().get(0).getBalance());
        money("660", secondPage.getClosingBalance());
    }

    @Test
    public void laterCreditDoesNotReduceEarlierAgeingAndDueDayIsNotOverdue() {
        APFixture fixture = apFixture(1);
        APInvoice invoice = matched(fixture, fixture.receive(1, 0), 1, "60");
        invoice = invoices().postInvoice(invoice.getId(), invoice.getVersion(), Dates.addDays(Dates.today(), -2), billingActor);
        APCredit credit = invoices().proposeCredit(invoice.getId(), invoice.getVersion(), creditInput(invoice, "10"), billingActor);
        invoices().approveCredit(credit.getId(), credit.getVersion(), manager);
        Date yesterday = Dates.addDays(Dates.today(), -1);
        money("66", reports().searchOpenItems(search(fixture.supplier), yesterday, false, billingActor).getItems().get(0).getOutstandingAmount());
        assertEquals(0, reports().searchOpenItems(search(fixture.supplier), yesterday, true, billingActor).getTotal());
        money("55", reports().searchOpenItems(search(fixture.supplier), Dates.today(), true, billingActor).getItems().get(0).getOutstandingAmount());
        money("55", reports().getStatement(fixture.supplier.getId(), Dates.addDays(Dates.today(), -2),
                Dates.today(), 1, 25, billingActor).getClosingBalance());
    }

    @Test
    public void statementCutoffExcludesInvoicesPostedAfterCutoff() {
        APFixture fixture = apFixture(1);
        posted(fixture, fixture.receive(1, 0), 1);
        Date yesterday = Dates.addDays(Dates.today(), -1);
        APStatement result = reports().getStatement(fixture.supplier.getId(), Dates.addDays(Dates.today(), -10),
                yesterday, 1, 25, billingActor);
        money("0", result.getClosingBalance());
        assertEquals(0, result.getEntries().getTotal());
        assertEquals(0, reports().searchOpenItems(search(fixture.supplier), yesterday, false, billingActor).getTotal());
    }

    @Test
    public void paginatedReceiptQueueAndOpenItemsHaveStableTotals() {
        APFixture fixture = apFixture(3);
        fixture.receive(1, 0);
        fixture.receive(1, 0);
        fixture.receive(1, 0);
        APSearch search = search(fixture.supplier);
        search.setSize(1);
        Long first = reports().searchAvailableReceipts(search, billingActor).getItems().get(0).getReceiptLineId();
        search.setPage(2);
        assertEquals(3, reports().searchAvailableReceipts(search, billingActor).getTotal());
        assertNotEquals(first, reports().searchAvailableReceipts(search, billingActor).getItems().get(0).getReceiptLineId());
    }

    @Test
    public void postedFactsAndMatchedProvenanceCannotBeChangedBySql() throws Exception {
        APFixture fixture = apFixture(1);
        APInvoice invoice = posted(fixture, fixture.receive(1, 0), 1);
        assertFrozen("update ap_invoice set supplier_invoice_number='ALTERED' where id=?", invoice.getId());
        assertFrozen("update ap_invoice_line set description='ALTERED' where invoice_id=?", invoice.getId());
        assertFrozen("update ap_match set quantity=1 where invoice_line_id=?", invoice.getLines().get(0).getId());
        APCredit credit = invoices().proposeCredit(invoice.getId(), invoice.getVersion(), creditInput(invoice, "10"), billingActor);
        credit = invoices().approveCredit(credit.getId(), credit.getVersion(), manager);
        assertFrozen("update ap_credit set reason='ALTERED' where id=?", credit.getId());
        APPaymentVoucher voucher = settlements().pay(paymentInput(invoices().getInvoice(invoice.getId(), billingActor), "10"), billingActor);
        assertFrozen("update ap_payment_voucher set amount=20 where id=?", voucher.getId());
    }

    @Test
    public void permissionsStaleVersionsAndInvalidDateRangesFailWithoutMutation() {
        final APFixture fixture = apFixture(1);
        final APInvoice invoice = matched(fixture, fixture.receive(1, 0), 1, "60");
        expect("permission.denied", new Runnable() {
            public void run() { invoices().postInvoice(invoice.getId(), invoice.getVersion(), warehouseActor); }
        });
        expect("concurrent.update", new Runnable() {
            public void run() { invoices().postInvoice(invoice.getId(), invoice.getVersion() + 1, billingActor); }
        });
        expect("ap.futureDate", new Runnable() {
            public void run() { invoices().postInvoice(invoice.getId(), invoice.getVersion(), Dates.addDays(Dates.today(), 1), billingActor); }
        });
        expect("ap.statementRange", new Runnable() {
            public void run() { reports().getStatement(fixture.supplier.getId(), Dates.today(),
                    Dates.addDays(Dates.today(), -1), 1, 25, billingActor); }
        });
        assertEquals("DRAFT", invoices().getInvoice(invoice.getId(), billingActor).getStatus());
    }

    private void assertFrozen(String sql, Long id) throws Exception {
        DataSource dataSource = context.getBean("dataSource", DataSource.class);
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            statement.setLong(1, id);
            try {
                statement.executeUpdate();
                fail("Immutable ledger mutation should fail");
            } catch (SQLException expected) {
                assertEquals("P0001", expected.getSQLState());
            } finally {
                connection.rollback();
            }
        }
    }
}
