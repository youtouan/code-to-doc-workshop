package jp.co.tsubame.wholesale.integration;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.OrderInput;
import jp.co.tsubame.wholesale.common.QuotationCommand;
import jp.co.tsubame.wholesale.common.QuotationLineCommand;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.PriceAgreement;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.Quotation;
import jp.co.tsubame.wholesale.entity.QuotationEvent;
import jp.co.tsubame.wholesale.entity.QuotationLine;
import jp.co.tsubame.wholesale.entity.QuotationRevision;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.Warehouse;
import jp.co.tsubame.wholesale.service.OrderService;
import jp.co.tsubame.wholesale.service.QuotationService;
import org.junit.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.Assert.*;

public class QuoteWorkflowTest extends PostgresTestSupport {
    @Test
    public void quotationServiceUsesRealXmlTransactions() {
        assertTrue(AopUtils.isAopProxy(quotes()));
        assertTrue(AopUtils.isAopProxy(orders()));
    }

    @Test
    public void catalogQuantityTiersAndMixedTaxesAreSnapshottedAtDraftCreation() {
        Fixture fixture = fixture(0);
        PriceAgreement agreement = new PriceAgreement();
        agreement.setCustomer(fixture.customer);
        agreement.setProduct(fixture.product);
        agreement.setValidFrom(Dates.parse("2020-01-01"));
        agreement.setMinimumQuantity(10);
        agreement.setUnitPrice(new BigDecimal("80.00"));
        catalog.savePriceAgreement(agreement, 0, manager);
        Product food = newProduct(uniqueCode("FOOD"), 1);
        food.setTaxCategory("REDUCED");
        food.setListPrice(new BigDecimal("120.00"));
        food = catalog.saveProduct(food, food.getVersion(), manager);
        QuotationCommand input = input(fixture, 10);
        input.getLines().add(line(food, 2, null));
        Quotation quote = quotes().saveDraft(null, 0, input, sales);
        assertEquals(new BigDecimal("80.00"), quote.getLines().get(0).getUnitPrice());
        assertEquals(new BigDecimal("1040.00"), quote.getNetAmount());
        assertEquals(new BigDecimal("99.00"), quote.getTaxAmount());
        assertEquals(64, quote.getCurrentRevision().getFingerprint().length());
    }

    @Test
    public void salesCanProposeNegotiatedPricingButNormalOrderOverridesRemainManagerOnly() {
        final Fixture fixture = fixture(0);
        QuotationCommand input = input(fixture, 3);
        input.getLines().get(0).setNegotiatedUnitPrice(new BigDecimal("10.01"));
        input.getLines().get(0).setNegotiationReason("架空数量条件の交渉提案");
        Quotation quote = quotes().saveDraft(null, 0, input, sales);
        assertEquals(new BigDecimal("30.03"), quote.getNetAmount());
        assertTrue(quote.getLines().get(0).isNegotiated());
        final OrderInput ordinary = fixture.input(3);
        ordinary.getLines().get(0).setPriceOverride(new BigDecimal("10.01"));
        ordinary.getLines().get(0).setPriceReason("見積書があるという自己申告");
        expect("permission.denied", new Runnable() {
            public void run() { orders().saveDraft(null, 0, ordinary, sales); }
        });
    }

    @Test
    public void everySavedRevisionKeepsPreviousLinesAndActualEventHistory() {
        Fixture fixture = fixture(0);
        Quotation quote = quotes().saveDraft(null, 0, input(fixture, 2), sales);
        quote = quotes().saveDraft(quote.getId(), quote.getVersion(), input(fixture, 3), sales);
        assertEquals(2, quote.getRevisionNumber());
        assertEquals(2, quote.getRevisions().size());
        assertEquals(2, quotes().getRevision(quote.getId(), 1, sales).getLines().get(0).getQuantity());
        assertEquals(3, quote.getLines().get(0).getQuantity());
        List<QuotationEvent> events = quotes().listEvents(quote.getId(), sales);
        assertEquals(2, events.size());
        assertEquals("NEW", events.get(0).getFromStatus());
        assertEquals("QUOTE_SAVE", events.get(1).getOperation());
        assertEquals(2, events.get(1).getRevisionNumber());
    }

    @Test
    public void revisingAcceptedProposalInvalidatesApprovalWithoutErasingItsHistory() {
        Fixture fixture = fixture(0);
        Quotation original = accepted(fixture, 2);
        Quotation revised = quotes().revise(original.getId(), original.getVersion(), input(fixture, 4),
                "架空顧客の増量依頼", sales);
        assertEquals("DRAFT", revised.getStatus());
        assertEquals(2, revised.getRevisionNumber());
        assertNull(revised.getApprovedById());
        assertNull(revised.getAcceptedOn());
        assertEquals("", revised.getAcceptanceReference());
        assertEquals(2, revised.getRevisions().get(0).getLines().get(0).getQuantity());
        boolean approvalPreserved = false;
        for (QuotationEvent event : quotes().listEvents(revised.getId(), manager)) {
            if ("QUOTE_APPROVE".equals(event.getOperation())) {
                assertEquals(1, event.getRevisionNumber());
                assertEquals(manager.getUserId(), event.getActorId());
                approvalPreserved = true;
            }
        }
        assertTrue(approvalPreserved);
    }

    @Test
    public void approvalUsesActorIdsAndAlsoExcludesTheCurrentRevisionAuthor() {
        Fixture fixture = fixture(0);
        Quotation quote = quotes().saveDraft(null, 0, input(fixture, 1), sales);
        final Quotation submitted = quotes().submit(quote.getId(), quote.getVersion(), sales);
        final Actor renamedCreator = new Actor(sales.getUserId(), "renamed-sales", "別表示", "MANAGER");
        expect("approval.self", new Runnable() {
            public void run() { quotes().approve(submitted.getId(), submitted.getVersion(), renamedCreator); }
        });
        quote = quotes().revise(submitted.getId(), submitted.getVersion(), input(fixture, 2), "管理者による提案修正", manager);
        final Quotation managerAuthored = quotes().submit(quote.getId(), quote.getVersion(), sales);
        expect("approval.self", new Runnable() {
            public void run() { quotes().approve(managerAuthored.getId(), managerAuthored.getVersion(), manager); }
        });
        assertEquals("APPROVED", quotes().approve(managerAuthored.getId(), managerAuthored.getVersion(), admin).getStatus());
    }

    @Test
    public void acceptanceNeedsAnApprovedProposalARealReferenceAndAValidDate() {
        Fixture fixture = fixture(0);
        final Quotation draft = quotes().saveDraft(null, 0, input(fixture, 1), sales);
        expect("quotation.state", new Runnable() {
            public void run() { quotes().accept(draft.getId(), draft.getVersion(), Dates.today(), "架空PO-1", sales); }
        });
        final Quotation approved = approve(draft);
        expect("quotation.acceptanceDate", new Runnable() {
            public void run() { quotes().accept(approved.getId(), approved.getVersion(), Dates.addDays(Dates.today(), 1), "架空PO-1", sales); }
        });
        expect("validation.required", new Runnable() {
            public void run() { quotes().accept(approved.getId(), approved.getVersion(), Dates.today(), " ", sales); }
        });
        Quotation accepted = quotes().accept(approved.getId(), approved.getVersion(), Dates.today(), "架空顧客PO-1", sales);
        assertEquals(sales.getUserId(), accepted.getAcceptedById());
        assertEquals("架空顧客PO-1", accepted.getAcceptanceReference());
    }

    @Test
    public void expiredProposalsCannotBeSubmittedAndCanBeExplicitlyRevised() {
        final Fixture fixture = fixture(0);
        QuotationCommand input = input(fixture, 1);
        input.setQuoteDate(Dates.addDays(Dates.today(), -10));
        input.setValidUntil(Dates.addDays(Dates.today(), -1));
        final Quotation old = quotes().saveDraft(null, 0, input, sales);
        expect("quotation.expired", new Runnable() {
            public void run() { quotes().submit(old.getId(), old.getVersion(), sales); }
        });
        Quotation expired = quotes().expire(old.getId(), old.getVersion(), batchActor);
        assertEquals("EXPIRED", expired.getStatus());
        Quotation revised = quotes().revise(expired.getId(), expired.getVersion(), input(fixture, 1), "有効期限の再提案", sales);
        assertEquals("APPROVED", approve(revised).getStatus());
    }

    @Test
    public void withdrawnAndRejectedProposalsNeedFreshApproval() {
        final Fixture fixture = fixture(0);
        Quotation quote = approve(quotes().saveDraft(null, 0, input(fixture, 1), sales));
        quote = quotes().withdraw(quote.getId(), quote.getVersion(), "架空交渉を再開", sales);
        assertEquals("WITHDRAWN", quote.getStatus());
        assertNull(quote.getApprovedById());
        quote = quotes().submit(quote.getId(), quote.getVersion(), sales);
        quote = quotes().reject(quote.getId(), quote.getVersion(), "架空条件の再確認", manager);
        assertEquals("REJECTED", quote.getStatus());
        quote = quotes().cancel(quote.getId(), quote.getVersion(), "架空案件中止", sales);
        final Quotation cancelled = quote;
        expect("quotation.terminal", new Runnable() {
            public void run() { quotes().revise(cancelled.getId(), cancelled.getVersion(), input(fixture, 1), "再開", sales); }
        });
    }

    @Test
    public void conversionPreservesNegotiatedPriceTaxRoundingAndProductSnapshots() {
        Fixture fixture = fixture(0);
        QuotationCommand input = input(fixture, 10);
        input.getLines().get(0).setNegotiatedUnitPrice(new BigDecimal("90.00"));
        input.getLines().get(0).setNegotiationReason("架空一括購入合意");
        Quotation quote = approve(quotes().saveDraft(null, 0, input, sales));
        quote = quotes().accept(quote.getId(), quote.getVersion(), Dates.today(), "架空承諾番号", sales);
        String originalName = quote.getLines().get(0).getProductName();
        Product current = catalog.getProduct(fixture.product.getId(), manager);
        current.setListPrice(new BigDecimal("175.00"));
        current.setTaxCategory("REDUCED");
        current.setPackSize(2);
        current.setName("架空改名商品");
        catalog.saveProduct(current, current.getVersion(), manager);
        Customer customer = catalog.getCustomer(fixture.customer.getId(), manager);
        customer.setTaxRounding("UP");
        catalog.saveCustomer(customer, customer.getVersion(), manager);
        SalesOrder order = quotes().convertToOrder(quote.getId(), quote.getVersion(), sales);
        assertEquals("DRAFT", order.getStatus());
        assertEquals(sales.getLogin(), order.getCreatedBy());
        assertEquals(new BigDecimal("90.00"), order.getLines().get(0).getUnitPrice());
        assertEquals(new BigDecimal("0.1000"), order.getLines().get(0).getTaxRate());
        assertEquals(1, order.getLines().get(0).getPackSize());
        assertEquals(originalName, order.getLines().get(0).getProductName());
        assertEquals("DOWN", order.getTaxRounding());
        assertEquals(new BigDecimal("990.00"), order.getTotalAmount());
        SalesOrder submitted = orders().submit(order.getId(), order.getVersion(), sales);
        assertEquals("APPROVED", orders().approve(submitted.getId(), submitted.getVersion(), manager).getStatus());
    }

    @Test
    public void conversionRequiresAcceptanceAndEnforcesCallerVersion() {
        Fixture fixture = fixture(0);
        final Quotation approved = approve(quotes().saveDraft(null, 0, input(fixture, 1), sales));
        expect("quotation.notAccepted", new Runnable() {
            public void run() { orders().convertApprovedQuotation(approved.getId(), approved.getVersion(), sales); }
        });
        final Quotation accepted = quotes().accept(approved.getId(), approved.getVersion(), Dates.today(), "架空PO", sales);
        expect("concurrent.update", new Runnable() {
            public void run() { quotes().convertToOrder(accepted.getId(), accepted.getVersion() - 1, sales); }
        });
    }

    @Test
    public void customerHoldAndProductInactivationAreRecheckedAtConversion() {
        final Fixture fixture = fixture(0);
        final Quotation quote = accepted(fixture, 1);
        Customer customer = catalog.getCustomer(fixture.customer.getId(), manager);
        customer.setOnHold(true);
        catalog.saveCustomer(customer, customer.getVersion(), manager);
        expect("quotation.customer", new Runnable() {
            public void run() { quotes().convertToOrder(quote.getId(), quote.getVersion(), sales); }
        });
        customer = catalog.getCustomer(fixture.customer.getId(), manager);
        customer.setOnHold(false);
        catalog.saveCustomer(customer, customer.getVersion(), manager);
        Product product = catalog.getProduct(fixture.product.getId(), manager);
        product.setActive(false);
        catalog.saveProduct(product, product.getVersion(), manager);
        expect("quotation.product", new Runnable() {
            public void run() { quotes().convertToOrder(quote.getId(), quote.getVersion(), sales); }
        });
        assertNull(quotes().getQuotation(quote.getId(), sales).getConvertedOrder());
    }

    @Test
    public void stoppedWarehouseAndChangedPackPreventConversion() {
        Fixture fixture = fixture(0);
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(uniqueCode("QW"));
        warehouse.setName("架空見積倉庫");
        warehouse = catalog.saveWarehouse(warehouse, 0, manager);
        QuotationCommand input = input(fixture, 3);
        input.setWarehouseId(warehouse.getId());
        Quotation approved = approve(quotes().saveDraft(null, 0, input, sales));
        final Quotation quote = quotes().accept(approved.getId(), approved.getVersion(), Dates.today(), "架空PO", sales);
        warehouse.setActive(false);
        catalog.saveWarehouse(warehouse, warehouse.getVersion(), manager);
        expect("quotation.warehouse", new Runnable() {
            public void run() { quotes().convertToOrder(quote.getId(), quote.getVersion(), sales); }
        });
        warehouse = catalog.getWarehouse(warehouse.getId(), manager);
        warehouse.setActive(true);
        catalog.saveWarehouse(warehouse, warehouse.getVersion(), manager);
        Product product = catalog.getProduct(fixture.product.getId(), manager);
        product.setPackSize(2);
        catalog.saveProduct(product, product.getVersion(), manager);
        expect("quotation.packChanged", new Runnable() {
            public void run() { quotes().convertToOrder(quote.getId(), quote.getVersion(), sales); }
        });
    }

    @Test
    public void anElapsedPromisedDateRequiresRevisionInsteadOfSilentRescheduling() {
        Fixture fixture = fixture(0);
        QuotationCommand input = input(fixture, 1);
        input.setQuoteDate(Dates.addDays(Dates.today(), -5));
        input.setRequestedDate(Dates.addDays(Dates.today(), -1));
        Quotation approved = approve(quotes().saveDraft(null, 0, input, sales));
        final Quotation accepted = quotes().accept(approved.getId(), approved.getVersion(), Dates.today(), "架空PO", sales);
        expect("quotation.deliveryExpired", new Runnable() {
            public void run() { quotes().convertToOrder(accepted.getId(), accepted.getVersion(), sales); }
        });
    }

    @Test
    public void simultaneousConversionRetriesCreateExactlyOneOrderAndOneConversionEvent() throws Exception {
        Fixture fixture = fixture(0);
        final Quotation quote = accepted(fixture, 2);
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Long> convert = new Callable<Long>() {
                public Long call() throws Exception {
                    assertTrue(start.await(15, TimeUnit.SECONDS));
                    return quotes().convertToOrder(quote.getId(), quote.getVersion(), sales).getId();
                }
            };
            Future<Long> first = executor.submit(convert);
            Future<Long> second = executor.submit(convert);
            start.countDown();
            assertEquals(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            Quotation converted = quotes().getQuotation(quote.getId(), sales);
            assertEquals("CONVERTED", converted.getStatus());
            assertEquals(converted.getConvertedOrder().getId(),
                    orders().convertApprovedQuotation(quote.getId(), quote.getVersion(), sales).getId());
            int conversions = 0;
            for (QuotationEvent event : quotes().listEvents(quote.getId(), sales)) {
                if ("QUOTE_CONVERT".equals(event.getOperation())) { conversions++; }
            }
            assertEquals(1, conversions);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void trustedConversionDetectsAlteredPersistedPricing() {
        Fixture fixture = fixture(0);
        final Quotation quote = accepted(fixture, 2);
        final Long lineId = quote.getLines().get(0).getId();
        transaction().execute(new TransactionCallback<Object>() {
            public Object doInTransaction(TransactionStatus status) {
                dao().session().createSQLQuery("update quotation_line set unit_price=1.00,catalog_unit_price=1.00 where id=:id")
                        .setLong("id", lineId).executeUpdate();
                return null;
            }
        });
        expect("quotation.snapshotChanged", new Runnable() {
            public void run() { quotes().convertToOrder(quote.getId(), quote.getVersion(), sales); }
        });
        assertNull(quotes().getQuotation(quote.getId(), sales).getConvertedOrder());
    }

    @Test
    public void conversionAndItsOrderRollbackTogetherWhenTheOuterTransactionFails() {
        Fixture fixture = fixture(0);
        final Quotation quote = accepted(fixture, 2);
        expect("test.rollback", new Runnable() {
            public void run() {
                transaction().execute(new TransactionCallback<Object>() {
                    public Object doInTransaction(TransactionStatus status) {
                        SalesOrder created = quotes().convertToOrder(quote.getId(), quote.getVersion(), sales);
                        assertNotNull(created.getId());
                        dao().flush();
                        throw new BusinessException("test.rollback", "検証用の外側トランザクション失敗");
                    }
                });
            }
        });
        Quotation retained = quotes().getQuotation(quote.getId(), sales);
        assertEquals("ACCEPTED", retained.getStatus());
        assertNull(retained.getConvertedOrder());
        Search search = new Search();
        search.setCustomerId(fixture.customer.getId());
        assertEquals(0L, orders().searchOrders(search, sales).getTotal());
        SalesOrder retried = quotes().convertToOrder(quote.getId(), quote.getVersion(), sales);
        assertNotNull(retried.getId());
        assertEquals(1L, orders().searchOrders(search, sales).getTotal());
    }

    @Test
    public void historicRevisionEntitiesCannotBeOverwrittenThroughHibernateDirtyChecking() {
        Fixture fixture = fixture(0);
        final Quotation quote = quotes().saveDraft(null, 0, input(fixture, 1), sales);
        transaction().execute(new TransactionCallback<Object>() {
            public Object doInTransaction(TransactionStatus status) {
                QuotationLine line = dao().get(QuotationLine.class, quote.getLines().get(0).getId());
                line.setUnitPrice(new BigDecimal("1.00"));
                dao().flush();
                return null;
            }
        });
        assertEquals(new BigDecimal("100.00"), quotes().getQuotation(quote.getId(), sales).getLines().get(0).getUnitPrice());
    }

    @Test
    public void roleChecksAndSearchAreUsableWithoutAnOpenSessionInView() {
        final Fixture fixture = fixture(0);
        final Quotation quote = quotes().saveDraft(null, 0, input(fixture, 1), sales);
        expect("permission.denied", new Runnable() {
            public void run() { quotes().saveDraft(null, 0, input(fixture, 1), warehouseActor); }
        });
        expect("permission.denied", new Runnable() {
            public void run() { quotes().approve(quote.getId(), quote.getVersion(), sales); }
        });
        Search search = new Search();
        search.setText(quote.getNumber());
        search.setCustomerId(fixture.customer.getId());
        search.setStatus("DRAFT");
        Quotation found = quotes().searchQuotations(search, warehouseActor).getItems().get(0);
        assertEquals(1, found.getLines().size());
        assertEquals(1, found.getRevisions().size());
        assertNotNull(found.getCurrentRevision().getAuthoredAt());
    }

    private Quotation accepted(Fixture fixture, int quantity) {
        Quotation approved = approve(quotes().saveDraft(null, 0, input(fixture, quantity), sales));
        return quotes().accept(approved.getId(), approved.getVersion(), Dates.today(), uniqueCode("CUSTOMERPO"), sales);
    }

    private Quotation approve(Quotation draft) {
        Quotation submitted = quotes().submit(draft.getId(), draft.getVersion(), sales);
        return quotes().approve(submitted.getId(), submitted.getVersion(), manager);
    }

    private QuotationCommand input(Fixture fixture, int quantity) {
        QuotationCommand input = new QuotationCommand();
        input.setCustomerId(fixture.customer.getId());
        input.setWarehouseId(fixture.warehouse.getId());
        input.setQuoteDate(Dates.today());
        input.setValidUntil(Dates.addDays(Dates.today(), 30));
        input.setRequestedDate(Dates.addDays(Dates.today(), 3));
        input.setExternalReference(uniqueCode("QREF"));
        input.getLines().add(line(fixture.product, quantity, null));
        return input;
    }

    private QuotationLineCommand line(Product product, int quantity, BigDecimal negotiated) {
        QuotationLineCommand line = new QuotationLineCommand();
        line.setProductId(product.getId());
        line.setQuantity(quantity);
        line.setNegotiatedUnitPrice(negotiated);
        return line;
    }

    private QuotationService quotes() { return service("quotationService", QuotationService.class); }
    private OrderService orders() { return service("orderService", OrderService.class); }
    private WholesaleDao dao() { return service("wholesaleDao", WholesaleDao.class); }
    private TransactionTemplate transaction() { return new TransactionTemplate(service("transactionManager", PlatformTransactionManager.class)); }
    private void expect(String code, Runnable action) {
        try { action.run(); fail("Expected " + code); }
        catch (BusinessException ex) { assertEquals(code, ex.getCode()); }
    }
}
