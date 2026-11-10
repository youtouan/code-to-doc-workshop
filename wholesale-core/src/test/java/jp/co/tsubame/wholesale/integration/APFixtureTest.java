package jp.co.tsubame.wholesale.integration;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import jp.co.tsubame.wholesale.common.APCreditInput;
import jp.co.tsubame.wholesale.common.APCreditLineInput;
import jp.co.tsubame.wholesale.common.APInvoiceInput;
import jp.co.tsubame.wholesale.common.APInvoiceLineInput;
import jp.co.tsubame.wholesale.common.APMatchInput;
import jp.co.tsubame.wholesale.common.APPaymentInput;
import jp.co.tsubame.wholesale.common.APPaymentLineInput;
import jp.co.tsubame.wholesale.common.APSearch;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.PurchasingReceiptCommand;
import jp.co.tsubame.wholesale.common.PurchasingReceiptLineCommand;
import jp.co.tsubame.wholesale.entity.APInvoice;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.PurchaseOrder;
import jp.co.tsubame.wholesale.entity.PurchaseOrderLine;
import jp.co.tsubame.wholesale.entity.PurchaseReceipt;
import jp.co.tsubame.wholesale.entity.PurchaseReceiptLine;
import jp.co.tsubame.wholesale.entity.Supplier;
import jp.co.tsubame.wholesale.entity.SupplierProduct;
import jp.co.tsubame.wholesale.service.APInvoiceService;
import jp.co.tsubame.wholesale.service.APReportService;
import jp.co.tsubame.wholesale.service.APSettlementService;
import jp.co.tsubame.wholesale.service.PurchasingService;
import org.junit.Assert;

public abstract class APFixtureTest extends PostgresTestSupport {
    protected APInvoiceService invoices() { return service("apInvoiceService", APInvoiceService.class); }
    protected APSettlementService settlements() { return service("apSettlementService", APSettlementService.class); }
    protected APReportService reports() { return service("apReportService", APReportService.class); }
    protected PurchasingService purchasing() { return service("purchasingService", PurchasingService.class); }

    protected APFixture apFixture(int ordered) { return apFixture(ordered, "60.00"); }

    protected APFixture apFixture(int ordered, String cost) {
        Supplier supplier = new Supplier();
        supplier.setCode(uniqueCode("APS"));
        supplier.setName("架空買掛試験仕入先");
        supplier.setAddress("架空県買掛試験市");
        supplier.setDefaultLeadTimeDays(0);
        supplier = purchasing().saveSupplier(supplier, 0, manager);
        Product product = newProduct(uniqueCode("APP"), 1);
        SupplierProduct offer = new SupplierProduct();
        offer.setSupplier(supplier);
        offer.setProduct(product);
        offer.setValidFrom(Dates.parse("2020-01-01"));
        offer.setMinimumQuantity(1);
        offer.setOrderPackSize(1);
        offer.setUnitCost(new BigDecimal(cost));
        offer.setLeadTimeDays(0);
        offer.setPreferred(true);
        purchasing().saveSupplierProduct(offer, 0, manager);
        PurchaseOrder order = new PurchaseOrder();
        order.setSupplier(supplier);
        order.setWarehouse(catalog.getWarehouse(21L, warehouseActor));
        order.setOrderDate(Dates.addDays(Dates.today(), -5));
        order.setExpectedDate(Dates.addDays(Dates.today(), -3));
        order.setNotes("AP isolated fixture");
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setProduct(product);
        line.setQuantity(ordered);
        order.getLines().add(line);
        order = purchasing().saveOrder(order, 0, warehouseActor);
        order = purchasing().submitOrder(order.getId(), order.getVersion(), warehouseActor);
        order = purchasing().approveOrder(order.getId(), order.getVersion(), manager);
        return new APFixture(supplier, product, order, new BigDecimal(cost));
    }

    protected APMatchInput match(APInvoice invoice, PurchaseReceiptLine receipt, int quantity) {
        APMatchInput input = new APMatchInput();
        input.setInvoiceLineId(invoice.getLines().get(0).getId());
        input.setReceiptLineId(receipt.getId());
        input.setQuantity(quantity);
        return input;
    }

    protected APInvoice matched(APFixture fixture, PurchaseReceiptLine receipt, int quantity, String price) {
        APInvoice invoice = invoices().saveDraft(null, 0, fixture.invoiceInput(quantity, price), billingActor);
        return invoices().replaceMatches(invoice.getId(), invoice.getVersion(), Arrays.asList(match(invoice, receipt, quantity)), billingActor);
    }

    protected APInvoice posted(APFixture fixture, PurchaseReceiptLine receipt, int quantity) {
        APInvoice invoice = matched(fixture, receipt, quantity, fixture.cost.toPlainString());
        return invoices().postInvoice(invoice.getId(), invoice.getVersion(), billingActor);
    }

    protected APCreditInput creditInput(APInvoice invoice, String amount) {
        APCreditInput input = new APCreditInput();
        input.setSupplierCreditNumber(uniqueCode("SC"));
        input.setCreditDate(Dates.today());
        input.setReason("仕入先確認済みの財務値引、物理返品なし");
        APCreditLineInput line = new APCreditLineInput();
        line.setInvoiceLineId(invoice.getLines().get(0).getId());
        line.setNetAmount(new BigDecimal(amount));
        input.getLines().add(line);
        return input;
    }

    protected APPaymentInput paymentInput(APInvoice invoice, String amount) {
        APPaymentInput input = new APPaymentInput();
        input.setRequestKey(uniqueCode("APV"));
        input.setSupplierId(invoice.getSupplier().getId());
        input.setPaymentDate(Dates.today());
        input.setAmount(new BigDecimal(amount));
        input.setMethod("BANK_TRANSFER");
        input.setReference("試験照合");
        APPaymentLineInput line = new APPaymentLineInput();
        line.setInvoiceId(invoice.getId());
        line.setAmount(new BigDecimal(amount));
        input.getLines().add(line);
        return input;
    }

    protected APSearch search(Supplier supplier) {
        APSearch search = new APSearch();
        search.setSupplierId(supplier.getId());
        return search;
    }

    protected void money(String expected, BigDecimal actual) {
        Assert.assertEquals(expected + " != " + actual, 0, new BigDecimal(expected).compareTo(actual));
    }

    protected void expect(String code, Runnable operation) {
        try {
            operation.run();
            Assert.fail("Expected " + code);
        } catch (BusinessException ex) {
            Assert.assertEquals(code, ex.getCode());
        }
    }

    protected final class APFixture {
        protected final Supplier supplier;
        protected final Product product;
        protected PurchaseOrder order;
        protected final BigDecimal cost;

        APFixture(Supplier supplier, Product product, PurchaseOrder order, BigDecimal cost) {
            this.supplier = supplier;
            this.product = product;
            this.order = order;
            this.cost = cost;
        }

        protected PurchaseReceiptLine receive(int accepted, int rejected) {
            order = purchasing().getOrder(order.getId(), warehouseActor);
            PurchasingReceiptCommand command = new PurchasingReceiptCommand();
            command.setRequestKey(uniqueCode("APR"));
            command.setOrderId(order.getId());
            command.setExpectedVersion(order.getVersion());
            command.setReceiptDate(Dates.addDays(Dates.today(), -3));
            PurchasingReceiptLineCommand line = new PurchasingReceiptLineCommand();
            line.setOrderLineId(order.getLines().get(0).getId());
            line.setAcceptedQuantity(accepted);
            line.setRejectedQuantity(rejected);
            line.setRejectionReason(rejected > 0 ? "試験検品不合格" : "");
            command.getLines().add(line);
            PurchaseReceipt receipt = purchasing().receive(command, warehouseActor);
            return receipt.getLines().get(0);
        }

        protected APInvoiceInput invoiceInput(int quantity, String price) {
            APInvoiceInput input = new APInvoiceInput();
            input.setSupplierId(supplier.getId());
            input.setSupplierInvoiceNumber(uniqueCode("SI"));
            input.setInvoiceDate(Dates.addDays(Dates.today(), -2));
            input.setDueDate(Dates.addDays(Dates.today(), -1));
            APInvoiceLineInput line = new APInvoiceLineInput();
            line.setProductId(product.getId());
            line.setQuantity(quantity);
            line.setUnitPrice(new BigDecimal(price));
            line.setTaxRate(new BigDecimal("0.1000"));
            input.getLines().add(line);
            return input;
        }
    }
}
