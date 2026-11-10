package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.Warehouse;
import org.junit.Test;
import static org.junit.Assert.*;

public class CatalogRulesTest {
    @Test
    public void validFinancialTermsIncludeImmediatePaymentAndAllClosingDays() {
        Customer customer = customer();
        for (int day : new int[] { 10, 20, 31 }) {
            customer.setClosingDay(day);
            for (int term : new int[] { 0, 30, 120 }) {
                customer.setPaymentTermDays(term);
                CatalogRules.customer(customer);
            }
        }
    }

    @Test
    public void finiteAndOpenEndedIntervalsUseInclusiveDateBoundaries() {
        assertTrue(CatalogRules.overlaps(Dates.parse("2026-01-01"), Dates.parse("2026-01-31"),
                Dates.parse("2026-01-31"), Dates.parse("2026-02-28")));
        assertFalse(CatalogRules.overlaps(Dates.parse("2026-01-01"), Dates.parse("2026-01-31"),
                Dates.parse("2026-02-01"), null));
        assertTrue(CatalogRules.overlaps(Dates.parse("2026-01-01"), null,
                Dates.parse("2099-01-01"), null));
    }

    @Test(expected = BusinessException.class)
    public void reversedIntervalsAreRejected() {
        CatalogRules.interval(Dates.parse("2026-02-01"), Dates.parse("2026-01-31"));
    }

    @Test(expected = BusinessException.class)
    public void negativeCreditLimitIsRejected() {
        Customer customer = customer();
        customer.setCreditLimit(new BigDecimal("-0.01"));
        CatalogRules.customer(customer);
    }

    @Test(expected = BusinessException.class)
    public void fractionalCreditBeyondCentsIsRejected() {
        Customer customer = customer();
        customer.setCreditLimit(new BigDecimal("10.001"));
        CatalogRules.customer(customer);
    }

    @Test(expected = BusinessException.class)
    public void invalidClosingDayIsRejected() {
        Customer customer = customer();
        customer.setClosingDay(15);
        CatalogRules.customer(customer);
    }

    @Test(expected = BusinessException.class)
    public void excessPaymentTermsAreRejected() {
        Customer customer = customer();
        customer.setPaymentTermDays(121);
        CatalogRules.customer(customer);
    }

    @Test(expected = BusinessException.class)
    public void unknownRoundingModeIsRejected() {
        Customer customer = customer();
        customer.setTaxRounding("CEILING");
        CatalogRules.customer(customer);
    }

    @Test(expected = BusinessException.class)
    public void malformedPostalCodeIsRejected() {
        Customer customer = customer();
        customer.setPostalCode("not-a-code");
        CatalogRules.customer(customer);
    }

    @Test
    public void zeroPricesAndDisabledReorderingAreValid() {
        Product product = product();
        product.setListPrice(BigDecimal.ZERO);
        product.setStandardCost(BigDecimal.ZERO);
        product.setTaxCategory("EXEMPT");
        CatalogRules.product(product);
    }

    @Test(expected = BusinessException.class)
    public void zeroPackIsRejected() {
        Product product = product();
        product.setPackSize(0);
        CatalogRules.product(product);
    }

    @Test(expected = BusinessException.class)
    public void reorderQuantityMustRespectProductPack() {
        Product product = product();
        product.setPackSize(6);
        product.setReorderQuantity(10);
        CatalogRules.product(product);
    }

    @Test(expected = BusinessException.class)
    public void negativeReorderPointIsRejected() {
        Product product = product();
        product.setReorderPoint(-1);
        CatalogRules.product(product);
    }

    @Test(expected = BusinessException.class)
    public void unknownTaxCategoryIsRejected() {
        Product product = product();
        product.setTaxCategory("VAT");
        CatalogRules.product(product);
    }

    @Test(expected = BusinessException.class)
    public void warehouseRequiresAName() {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode("EAST");
        warehouse.setName(" ");
        CatalogRules.warehouse(warehouse);
    }

    private Customer customer() {
        Customer customer = new Customer();
        customer.setCode("C001");
        customer.setName("架空試験商会");
        return customer;
    }

    private Product product() {
        Product product = new Product();
        product.setCode("P001");
        product.setName("架空試験商品");
        return product;
    }
}
