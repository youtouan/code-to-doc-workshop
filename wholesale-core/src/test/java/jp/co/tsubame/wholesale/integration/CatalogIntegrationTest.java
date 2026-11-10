package jp.co.tsubame.wholesale.integration;

import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.PriceAgreement;
import jp.co.tsubame.wholesale.entity.Product;
import org.junit.Test;
import org.springframework.aop.support.AopUtils;
import static org.junit.Assert.*;

public class CatalogIntegrationTest extends PostgresTestSupport {
    @Test
    public void catalogIsATransactionalXmlProxy() {
        assertTrue(AopUtils.isAopProxy(catalog));
    }

    @Test
    public void readsRemainAvailableForInactiveHeldCustomers() {
        Fixture fixture = fixture(0);
        Customer customer = catalog.getCustomer(fixture.customer.getId(), sales);
        customer.setActive(false);
        customer.setOnHold(true);
        customer = catalog.saveCustomer(customer, customer.getVersion(), manager);
        assertTrue(catalog.getCustomer(customer.getId(), billingActor).isOnHold());
        assertEquals(new BigDecimal("100.00"),
                catalog.price(customer.getId(), fixture.product.getId(), 1, Dates.today(), sales));
    }

    @Test
    public void highestApplicableTierWinsAndIntervalsAreInclusive() {
        Fixture fixture = fixture(0);
        agreement(fixture, 1, "2026-01-01", "2026-12-31", "90.00");
        agreement(fixture, 10, "2026-01-01", "2026-12-31", "80.00");
        assertEquals(new BigDecimal("90.00"), catalog.price(fixture.customer.getId(),
                fixture.product.getId(), 9, Dates.parse("2026-01-01"), sales));
        assertEquals(new BigDecimal("80.00"), catalog.price(fixture.customer.getId(),
                fixture.product.getId(), 10, Dates.parse("2026-12-31"), sales));
        assertEquals(new BigDecimal("100.00"), catalog.price(fixture.customer.getId(),
                fixture.product.getId(), 10, Dates.parse("2027-01-01"), sales));
    }

    @Test
    public void sameTierOverlapIsRejectedButNextDayIsAllowed() {
        final Fixture fixture = fixture(0);
        agreement(fixture, 1, "2026-01-01", "2026-01-31", "90.00");
        expect("agreement.overlap", new Runnable() {
            public void run() { agreement(fixture, 1, "2026-01-31", "2026-02-28", "85.00"); }
        });
        agreement(fixture, 1, "2026-02-01", null, "85.00");
        assertEquals(2, catalog.listPriceAgreements(fixture.customer.getId(), sales).size());
    }

    @Test
    public void simultaneousOverlapCreationIsSerializedByCustomerLock() throws Exception {
        final Fixture fixture = fixture(0);
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> attempt = new Callable<Boolean>() {
                public Boolean call() throws Exception {
                    assertTrue(start.await(15, TimeUnit.SECONDS));
                    try {
                        agreement(fixture, 1, "2026-01-01", null, "90.00");
                        return Boolean.TRUE;
                    } catch (BusinessException ex) {
                        assertEquals("agreement.overlap", ex.getCode());
                        return Boolean.FALSE;
                    }
                }
            };
            Future<Boolean> first = executor.submit(attempt);
            Future<Boolean> second = executor.submit(attempt);
            start.countDown();
            assertTrue(first.get(30, TimeUnit.SECONDS) != second.get(30, TimeUnit.SECONDS));
            assertEquals(1, catalog.listPriceAgreements(fixture.customer.getId(), sales).size());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void staleCustomerUpdatesCannotOverwriteFinancialTerms() {
        Fixture fixture = fixture(0);
        final Customer stale = catalog.getCustomer(fixture.customer.getId(), manager);
        Customer fresh = catalog.getCustomer(fixture.customer.getId(), manager);
        fresh.setCreditLimit(new BigDecimal("2000000.00"));
        catalog.saveCustomer(fresh, fresh.getVersion(), manager);
        stale.setCreditLimit(BigDecimal.ZERO);
        expect("concurrent.update", new Runnable() {
            public void run() { catalog.saveCustomer(stale, stale.getVersion(), manager); }
        });
        assertEquals(new BigDecimal("2000000.00"),
                catalog.getCustomer(stale.getId(), manager).getCreditLimit());
    }

    @Test
    public void batchCanMaintainProductsButNotFinancialMasters() {
        Fixture fixture = fixture(0);
        Product product = catalog.getProduct(fixture.product.getId(), batchActor);
        product.setListPrice(new BigDecimal("101.00"));
        assertEquals(new BigDecimal("101.00"), catalog.saveProduct(product, product.getVersion(), batchActor).getListPrice());
        final Customer customer = catalog.getCustomer(fixture.customer.getId(), batchActor);
        expect("permission.denied", new Runnable() {
            public void run() { catalog.saveCustomer(customer, customer.getVersion(), batchActor); }
        });
    }

    @Test
    public void searchEscapesSqlLikeWildcardsAndKeepsPagingStable() {
        Fixture fixture = fixture(0);
        Customer customer = catalog.getCustomer(fixture.customer.getId(), manager);
        String marker = "検索_%!専用" + uniqueCode("S");
        customer.setName("架空" + marker);
        catalog.saveCustomer(customer, customer.getVersion(), manager);
        Search search = new Search();
        search.setText(marker);
        search.setSize(1);
        assertEquals(1L, catalog.searchCustomers(search, sales).getTotal());
        assertEquals(customer.getId(), catalog.searchCustomers(search, sales).getItems().get(0).getId());
    }

    @Test
    public void deletingAContractRestoresListPriceAndChecksVersion() {
        Fixture fixture = fixture(0);
        final PriceAgreement agreement = agreement(fixture, 1, "2020-01-01", null, "90.00");
        expect("concurrent.update", new Runnable() {
            public void run() { catalog.deletePriceAgreement(agreement.getId(), agreement.getVersion() + 1, manager); }
        });
        catalog.deletePriceAgreement(agreement.getId(), agreement.getVersion(), manager);
        assertEquals(new BigDecimal("100.00"), catalog.price(fixture.customer.getId(),
                fixture.product.getId(), 1, Dates.today(), sales));
    }

    private PriceAgreement agreement(Fixture fixture, int minimum, String from, String to, String price) {
        PriceAgreement agreement = new PriceAgreement();
        agreement.setCustomer(fixture.customer);
        agreement.setProduct(fixture.product);
        agreement.setMinimumQuantity(minimum);
        agreement.setValidFrom(Dates.parse(from));
        agreement.setValidTo(to == null ? null : Dates.parse(to));
        agreement.setUnitPrice(new BigDecimal(price));
        return catalog.savePriceAgreement(agreement, 0, manager);
    }

    private void expect(String code, Runnable operation) {
        try {
            operation.run();
            fail("Expected " + code);
        } catch (BusinessException ex) {
            assertEquals(code, ex.getCode());
        }
    }
}
