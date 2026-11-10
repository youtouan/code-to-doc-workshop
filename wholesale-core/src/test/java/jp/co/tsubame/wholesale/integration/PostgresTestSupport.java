package jp.co.tsubame.wholesale.integration;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.AuthenticationResult;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.OrderInput;
import jp.co.tsubame.wholesale.common.OrderLineInput;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.Warehouse;
import jp.co.tsubame.wholesale.service.AuthService;
import jp.co.tsubame.wholesale.service.CatalogService;
import jp.co.tsubame.wholesale.service.InventoryService;
import jp.co.tsubame.wholesale.service.OrderService;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public abstract class PostgresTestSupport {
    protected static ClassPathXmlApplicationContext context;
    protected static CatalogService catalog;
    protected static AuthService auth;
    protected static Actor sales;
    protected static Actor warehouseActor;
    protected static Actor manager;
    protected static Actor billingActor;
    protected static Actor batchActor;
    protected static Actor admin;

    @BeforeClass
    public static void startContext() throws Exception {
        Assume.assumeTrue("Enable real PostgreSQL tests with -Ddb.tests=true", Boolean.getBoolean("db.tests"));
        context = new ClassPathXmlApplicationContext("application-context.xml");
        DataSource source = context.getBean("dataSource", DataSource.class);
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select current_database()")) {
            Assert.assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
            Assert.assertTrue(result.next());
            String database = result.getString(1);
            Assert.assertTrue("Refusing integration fixtures outside a dedicated test database",
                    database.equals("wholesale_test") || database.startsWith("wholesale_test_"));
        }
        catalog = context.getBean("catalogService", CatalogService.class);
        auth = context.getBean("authService", AuthService.class);
        sales = login("sales");
        warehouseActor = login("warehouse");
        manager = login("manager");
        billingActor = login("billing");
        batchActor = login("batch");
        admin = login("admin");
    }

    @AfterClass
    public static void stopContext() {
        if (context != null) {
            context.close();
            context = null;
        }
    }

    protected <T> T service(String name, Class<T> type) {
        return context.getBean(name, type);
    }

    protected Fixture fixture(int stock) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
        Customer customer = new Customer();
        customer.setCode("T" + suffix);
        customer.setName("試験用商店 " + suffix);
        customer.setActive(true);
        customer.setOnHold(false);
        customer.setCreditLimit(new BigDecimal("1000000.00"));
        customer.setClosingDay(31);
        customer.setPaymentTermDays(30);
        customer.setTaxRounding("DOWN");
        customer.setPostalCode("000-0000");
        customer.setAddress("架空県試験市1-1");
        customer.setTelephone("000-000-0000");
        customer.setNotes("isolated integration fixture");
        customer = catalog.saveCustomer(customer, 0, manager);
        Product product = newProduct("P" + suffix, 1);
        Warehouse warehouse = catalog.getWarehouse(21L, warehouseActor);
        if (stock > 0) {
            service("inventoryService", InventoryService.class).receive("TEST-" + suffix, warehouse.getId(),
                    product.getId(), stock, product.getStandardCost(), Dates.today(), "TEST-OPENING", "", warehouseActor);
        }
        return new Fixture(customer, product, warehouse);
    }

    protected Product newProduct(String code, int packSize) {
        Product product = new Product();
        product.setCode(code);
        product.setName("試験商品 " + code);
        product.setUnit("個");
        product.setActive(true);
        product.setTaxCategory("STANDARD");
        product.setListPrice(new BigDecimal("100.00"));
        product.setStandardCost(new BigDecimal("60.00"));
        product.setPackSize(packSize);
        product.setReorderPoint(5);
        product.setReorderQuantity(20);
        product.setNotes("isolated integration fixture");
        return catalog.saveProduct(product, 0, manager);
    }

    protected static Actor login(String login) {
        AuthenticationResult result = auth.authenticate(login, ("Demo-" + login + "-2026!").toCharArray());
        Assert.assertTrue(result.getMessage(), result.isAuthenticated());
        return result.getActor();
    }

    protected static String uniqueCode(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
    }

    protected final class Fixture {
        public final Customer customer;
        public final Product product;
        public final Warehouse warehouse;

        Fixture(Customer customer, Product product, Warehouse warehouse) {
            this.customer = customer;
            this.product = product;
            this.warehouse = warehouse;
        }

        public OrderInput input(int quantity) {
            OrderInput input = new OrderInput();
            input.setCustomerId(customer.getId());
            input.setWarehouseId(warehouse.getId());
            input.setOrderDate(Dates.addDays(Dates.today(), -2));
            input.setRequestedDate(Dates.addDays(Dates.today(), 1));
            input.setNotes("workflow fixture");
            OrderLineInput line = new OrderLineInput();
            line.setProductId(product.getId());
            line.setQuantity(quantity);
            input.getLines().add(line);
            return input;
        }

        public SalesOrder order(int quantity) {
            return service("orderService", OrderService.class).saveDraft(null, 0, input(quantity), sales);
        }

        public SalesOrder order(int quantity, Date orderDate) {
            OrderInput input = input(quantity);
            input.setOrderDate(orderDate);
            return service("orderService", OrderService.class).saveDraft(null, 0, input, sales);
        }
    }
}
