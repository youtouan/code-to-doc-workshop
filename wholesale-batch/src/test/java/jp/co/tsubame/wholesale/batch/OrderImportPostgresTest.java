package jp.co.tsubame.wholesale.batch;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import jp.co.tsubame.wholesale.batch.csv.CsvReader;
import jp.co.tsubame.wholesale.batch.csv.CsvRecord;
import jp.co.tsubame.wholesale.batch.csv.CsvWriter;
import jp.co.tsubame.wholesale.batch.entity.BatchRow;
import jp.co.tsubame.wholesale.batch.entity.BatchRun;
import jp.co.tsubame.wholesale.batch.service.BatchRowService;
import jp.co.tsubame.wholesale.batch.service.BatchRunService;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.AuthenticationResult;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Fingerprints;
import jp.co.tsubame.wholesale.common.ShipmentLineInput;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.Invoice;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.SalesOrderLine;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.entity.StockReceipt;
import jp.co.tsubame.wholesale.entity.Warehouse;
import jp.co.tsubame.wholesale.service.AuthService;
import jp.co.tsubame.wholesale.service.BillingService;
import jp.co.tsubame.wholesale.service.CatalogService;
import jp.co.tsubame.wholesale.service.InventoryService;
import jp.co.tsubame.wholesale.service.OrderService;
import jp.co.tsubame.wholesale.service.ShippingService;
import jp.co.tsubame.wholesale.service.UserAdminService;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import static org.junit.Assert.*;

/** Real PostgreSQL and real authenticated CLI calls; all fixtures are uniquely named. */
public class OrderImportPostgresTest {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static ClassPathXmlApplicationContext context;
    private static DataSource source;
    private static Actor admin;
    private static Actor manager;
    private static CatalogService catalog;
    private static OrderService orders;
    private static BatchRunService runs;
    private static BatchRowService worker;
    private Path directory;

    @BeforeClass public static void connect() throws Exception {
        Assume.assumeTrue("Enable after SQL070 with -Ddb.tests=true", Boolean.getBoolean("db.tests"));
        context = new ClassPathXmlApplicationContext("application-context.xml");
        source = context.getBean("dataSource", DataSource.class);
        try (Connection connection = source.getConnection()) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
            String database = connection.getCatalog();
            assertTrue("Use a dedicated test database", "wholesale_test".equals(database) || database.startsWith("wholesale_test_"));
        }
        admin = login("admin", "Demo-admin-2026!");
        manager = login("manager", "Demo-manager-2026!");
        catalog = context.getBean("catalogService", CatalogService.class);
        orders = context.getBean("orderService", OrderService.class);
        runs = context.getBean("batchRunService", BatchRunService.class);
        worker = context.getBean("batchRowService", BatchRowService.class);
    }

    @AfterClass public static void disconnect() {
        if (context != null) { context.close(); context = null; }
    }
    @Before public void prepare() throws Exception {
        directory = Paths.get("target", "order-import-" + UUID.randomUUID().toString());
        Files.createDirectories(directory);
    }
    @After public void cleanup() throws Exception {
        if (directory != null) {
            try (DirectoryStream<Path> paths = Files.newDirectoryStream(directory)) {
                for (Path path : paths) { Files.deleteIfExists(path); }
            }
            Files.delete(directory);
        }
    }

    @Test public void groupedMainImportUsesCorePricingAndNeverApprovesAndReplaysCanonically() throws Exception {
        Customer customer = customer(false);
        Product first = product(1, true);
        Product second = product(1, true);
        String external = key();
        String other = key();
        String runKey = key();
        List<String> a = line(external, customer, "EAST", first, 2, "notes\nsecond line");
        List<String> b = line(external, customer, "EAST", second, 3, "notes\nsecond line");
        Path input = csv(a, b, line(other, customer, "WEST", first, 1, "independent"));
        Cli firstRun = importFile(input, runKey);
        assertEquals(firstRun.error, 0, firstRun.code);
        assertTrue(firstRun.output.contains("total=2 success=2 rejected=0"));
        long id = orderId(external);
        SalesOrder order = orders.getOrder(id, admin);
        assertEquals("DRAFT", order.getStatus());
        assertNull(order.getApprovedAt());
        assertEquals(2, order.getLines().size());
        assertEquals(new BigDecimal("42.00"), order.getLines().get(0).getUnitPrice());
        assertEquals(0, new BigDecimal("0.10").compareTo(order.getLines().get(0).getTaxRate()));
        BatchRun run = runs.getRun(runKey, admin);
        List<BatchRow> journal = runs.listRows(run.getId(), 0, 10, admin);
        assertTrue(journal.get(0).getMessage().contains("source_rows=1..2"));
        assertTrue(journal.get(0).getMessage().contains("source_lines=2..5"));
        assertEquals(Long.valueOf(id), journal.get(0).getEntityId());
        assertTrue(importFile(input, runKey).output.contains("replayed=true"));
        first.setListPrice(new BigDecimal("999.00"));
        catalog.saveProduct(first, first.getVersion(), admin);
        a.set(8, "002");
        Path reordered = csv(b, a);
        assertEquals(0, importFile(reordered, key()).code);
        assertEquals(id, orderId(external));
        assertEquals(order.getVersion(), orders.getOrder(id, admin).getVersion());
        assertEquals(new BigDecimal("42.00"), orders.getOrder(id, admin).getLines().get(0).getUnitPrice());
        a.set(8, "4");
        assertEquals(3, importFile(csv(a, b), key()).code);
        assertEquals(1, count("select count(*) from batch_order_import where external_key=?", external));
        assertEquals(2, orders.getOrder(id, admin).getLines().get(0).getQuantity());
    }

    @Test public void inconsistentHeadersAndNoncontiguousKeysNeverCreateFragmentOrders() throws Exception {
        Customer customer = customer(false);
        Product product = product(1, true);
        Product second = product(1, true);
        String fragment = key();
        String control = key();
        String mismatch = key();
        List<String> badHeader = line(mismatch, customer, "WEST", second, 2, "same");
        String runKey = key();
        Path input = csv(line(fragment, customer, "EAST", product, 1, ""),
                line(control, customer, "EAST", product, 1, ""),
                line(fragment, customer, "EAST", second, 2, ""),
                line(mismatch, customer, "EAST", product, 1, "same"), badHeader);
        assertEquals(3, importFile(input, runKey).code);
        assertEquals(0, count("select count(*) from batch_order_import where external_key=?", fragment));
        assertEquals(0, count("select count(*) from batch_order_import where external_key=?", mismatch));
        assertTrue(orderId(control) > 0);
        BatchRun run = runs.getRun(runKey, admin);
        assertEquals(4, run.getTotalRows());
        assertEquals(3, run.getRejectedRows());
        List<BatchRow> rows = runs.listRows(run.getId(), 0, 10, admin);
        assertEquals("orderImport.noncontiguous", rows.get(0).getCode());
        assertEquals("orderImport.noncontiguous", rows.get(2).getCode());
        assertEquals("orderImport.headerConflict", rows.get(3).getCode());
    }

    @Test public void structuralOrUtf8FailureAnywherePreventsEveryOrderInTheFile() throws Exception {
        Customer customer = customer(false);
        Product product = product(1, true);
        for (byte[] malformed : Arrays.asList("\"unterminated".getBytes(UTF8), new byte[] {(byte) 0xff})) {
            String external = key();
            String runKey = key();
            Path input = csv(line(external, customer, "EAST", product, 1, ""));
            Files.write(input, malformed, java.nio.file.StandardOpenOption.APPEND);
            assertEquals(3, importFile(input, runKey).code);
            assertEquals(0, count("select count(*) from batch_order_import where external_key=?", external));
            assertEquals(0, count("select count(*) from sales_order where external_reference=?", external));
            BatchRun run = runs.getRun(runKey, admin);
            assertEquals("FAILED", run.getStatus());
            assertEquals(0, run.getSuccessRows());
            assertEquals(1, run.getRejectedRows());
        }
    }

    @Test public void wholeGroupRollsBackOnCorePackDateHoldAndInactiveMasterRules() throws Exception {
        Customer customer = customer(false);
        Customer held = customer(true);
        Product first = product(1, true);
        Product packed = product(10, true);
        Product inactive = product(1, false);
        Warehouse inactiveWarehouse = new Warehouse();
        inactiveWarehouse.setCode(code());
        inactiveWarehouse.setName("架空停止倉庫");
        inactiveWarehouse.setActive(false);
        inactiveWarehouse = catalog.saveWarehouse(inactiveWarehouse, 0, admin);
        String pack = key();
        String date = key();
        String hold = key();
        String stoppedProduct = key();
        String stoppedWarehouse = key();
        String good = key();
        List<String> future = line(date, customer, "EAST", first, 1, "");
        future.set(3, Dates.format(Dates.addDays(Dates.today(), 1)));
        future.set(4, future.get(3));
        String runKey = key();
        Path input = csv(line(pack, customer, "EAST", first, 1, ""), line(pack, customer, "EAST", packed, 1, ""),
                future, line(hold, held, "EAST", first, 1, ""),
                line(stoppedProduct, customer, "EAST", inactive, 1, ""),
                line(stoppedWarehouse, customer, inactiveWarehouse.getCode(), first, 1, ""),
                line(good, customer, "EAST", first, 1, ""));
        assertEquals(3, importFile(input, runKey).code);
        for (String bad : Arrays.asList(pack, date, hold, stoppedProduct, stoppedWarehouse)) {
            assertEquals(0, count("select count(*) from batch_order_import where external_key=?", bad));
            assertEquals(0, count("select count(*) from sales_order where external_reference=?", bad));
        }
        assertTrue(orderId(good) > 0);
        BatchRun run = runs.getRun(runKey, admin);
        assertEquals(6, run.getTotalRows());
        assertEquals(5, run.getRejectedRows());
        assertEquals("order.pack", runs.listRows(run.getId(), 0, 10, admin).get(0).getCode());
    }

    @Test public void plainBatchCannotImportButAuthenticatedDualRoleUserCan() throws Exception {
        Customer customer = customer(false);
        Product product = product(1, true);
        String external = key();
        Path input = csv(line(external, customer, "EAST", product, 1, ""));
        Cli refused = cli("batch", "Demo-batch-2026!", "import-orders", "--file", input.toString(), "--run-key", key());
        assertEquals(2, refused.code);
        assertTrue(refused.error.contains("permission.denied"));
        assertEquals(0, count("select count(*) from batch_order_import where external_key=?", external));
        String user = "csv" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String password = "Csv-Account-2026!";
        context.getBean("userAdminService", UserAdminService.class)
                .createUser(user, "架空CSV連携利用者", "BATCH,SALES", password.toCharArray(), admin);
        assertEquals(0, cli(user, password, "import-orders", "--file", input.toString(), "--run-key", key()).code);
        SalesOrder order = orders.getOrder(orderId(external), admin);
        assertEquals(user, order.getCreatedBy());
        assertEquals("DRAFT", order.getStatus());
        assertNull(order.getApprovedBy());
    }

    @Test public void concurrentExternalKeyCreatesExactlyOneOrderAndTwoSuccessfulJournals() throws Exception {
        Customer customer = customer(false);
        Product product = product(1, true);
        final String external = key();
        final OrderGroup group = readGroup(csv(line(external, customer, "EAST", product, 2, "")));
        final BatchRun first = start();
        final BatchRun second = start();
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch release = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            List<Future<BatchRow>> futures = new ArrayList<Future<BatchRow>>();
            for (final BatchRun run : Arrays.asList(first, second)) {
                futures.add(threads.submit(new Callable<BatchRow>() {
                    public BatchRow call() throws Exception {
                        ready.countDown();
                        if (!release.await(20, TimeUnit.SECONDS)) { throw new IllegalStateException("Start barrier timed out"); }
                        return worker.executeRow(run.getId(), new RowTask(group), admin);
                    }
                }));
            }
            assertTrue(ready.await(20, TimeUnit.SECONDS));
            release.countDown();
            BatchRow a = futures.get(0).get(45, TimeUnit.SECONDS);
            BatchRow b = futures.get(1).get(45, TimeUnit.SECONDS);
            assertEquals(a.getEntityId(), b.getEntityId());
            assertEquals("SUCCESS", a.getResult());
            assertEquals("SUCCESS", b.getResult());
            assertEquals(1, count("select count(*) from batch_order_import where external_key=?", external));
            assertEquals(1, count("select count(*) from sales_order where external_reference=?", external));
        } finally {
            release.countDown();
            threads.shutdownNow();
            assertTrue(threads.awaitTermination(15, TimeUnit.SECONDS));
            runs.recordComplete(first.getId(), false, "Concurrent import test completed", admin);
            runs.recordComplete(second.getId(), false, "Concurrent import test completed", admin);
        }
    }

    @Test public void failedSuccessJournalCommitRollsBackOrderAndExternalClaimTogether() throws Exception {
        Customer customer = customer(false);
        Product product = product(1, true);
        String external = key();
        List<String> fields = line(external, customer, "EAST", product, 2, "");
        OrderGroup invalidJournal = new OrderGroup(0, 1, new CsvRecord(2, fields));
        invalidJournal.add(1, new CsvRecord(2, fields));
        invalidJournal.seal();
        BatchRun run = start();
        try {
            worker.executeRow(run.getId(), new RowTask(invalidJournal), admin);
            fail("Expected PostgreSQL journal check to fail");
        } catch (RuntimeException expected) {
            assertFalse(expected instanceof jp.co.tsubame.wholesale.common.BusinessException);
        }
        assertEquals(0, count("select count(*) from batch_order_import where external_key=?", external));
        assertEquals(0, count("select count(*) from sales_order where external_reference=?", external));
        assertEquals(0, runs.getRun(run.getRunKey(), admin).getTotalRows());
        runs.recordFailed(run.getId(), "Deliberate journal failure verified", 1, admin);
    }

    @Test public void detailExportsPageFilterEscapeFormulasAndPreserveCoreAmountsAndInvoiceLink() throws Exception {
        Customer customer = customer(false);
        Product first = product(1, true);
        Product second = product(1, true);
        String external = key();
        Date day = Dates.monthEnd(Dates.addMonths(Dates.today(), -1));
        List<String> a = line(external, customer, "EAST", first, 2, "line notes");
        List<String> b = line(external, customer, "EAST", second, 3, "line notes");
        a.set(3, Dates.format(day)); a.set(4, Dates.format(day));
        b.set(3, Dates.format(day)); b.set(4, Dates.format(day));
        assertEquals(0, importFile(csv(a, b), key()).code);
        SalesOrder order = orders.getOrder(orderId(external), admin);
        long firstLine = Math.min(order.getLines().get(0).getId(), order.getLines().get(1).getId());
        Path page1 = directory.resolve("order-lines-1.csv");
        Cli exported = export("export-order-lines", page1, 1, firstLine - 1, day, "DRAFT");
        assertEquals(exported.error, 0, exported.code);
        assertTrue(exported.error.contains("has_more=true"));
        List<List<String>> rows = readCsv(page1);
        assertEquals(2, rows.size());
        assertTrue(value(rows, 1, "product_name").startsWith("'="));
        assertEquals("42.00", value(rows, 1, "unit_price"));
        long cursor = Long.parseLong(value(rows, 1, "id"));
        Path page2 = directory.resolve("order-lines-2.csv");
        assertEquals(0, export("export-order-lines", page2, 1, cursor, day, "DRAFT").code);
        assertFalse(value(readCsv(page2), 1, "id").equals(Long.toString(cursor)));
        assertEquals(2, export("export-order-lines", page1, 1, 0, day, "DRAFT").code);
        InventoryService inventory = context.getBean("inventoryService", InventoryService.class);
        StockReceipt receipt = inventory.receive(key(), 21L, first.getId(), 2, new BigDecimal("20.50"),
                day, "=REFERENCE", "=NOTE", admin);
        inventory.receive(key(), 21L, second.getId(), 3, new BigDecimal("20.50"), day, "reference", "note", admin);
        Path receiptFile = directory.resolve("receipts.csv");
        assertEquals(0, export("export-receipts", receiptFile, 1, receipt.getId() - 1, day, null).code);
        List<List<String>> receiptRows = readCsv(receiptFile);
        assertEquals("41.00", value(receiptRows, 1, "extended_cost"));
        assertEquals("'=REFERENCE", value(receiptRows, 1, "reference"));
        assertEquals("'=NOTE", value(receiptRows, 1, "note"));
        order = orders.submit(order.getId(), order.getVersion(), admin);
        order = orders.approve(order.getId(), order.getVersion(), manager);
        order = orders.allocate(order.getId(), order.getVersion(), admin);
        List<ShipmentLineInput> shippingLines = new ArrayList<ShipmentLineInput>();
        for (SalesOrderLine sourceLine : order.getLines()) {
            ShipmentLineInput line = new ShipmentLineInput();
            line.setOrderLineId(sourceLine.getId()); line.setQuantity(sourceLine.getQuantity()); shippingLines.add(line);
        }
        ShippingService shipping = context.getBean("shippingService", ShippingService.class);
        Shipment shipment = shipping.instruct(order.getId(), order.getVersion(), day, "OWN", "=SHIPMENT-NOTE", shippingLines, admin);
        long shipmentLine = Math.min(shipment.getLines().get(0).getId(), shipment.getLines().get(1).getId());
        Path instructedFile = directory.resolve("instructed.csv");
        assertEquals(0, export("export-shipments", instructedFile, 2, shipmentLine - 1, day, "INSTRUCTED").code);
        assertEquals("", value(readCsv(instructedFile), 1, "invoice_id"));
        shipment = shipping.confirm(shipment.getId(), shipment.getVersion(), day, "=TRACKING", admin);
        BillingService billing = context.getBean("billingService", BillingService.class);
        Invoice invoice = billing.prepare(customer.getId(), day, admin);
        invoice = billing.finalizeInvoice(invoice.getId(), invoice.getVersion(), admin);
        Path confirmedFile = directory.resolve("confirmed.csv");
        assertEquals(0, export("export-shipments", confirmedFile, 2, shipmentLine - 1, day, "CONFIRMED").code);
        List<List<String>> shipmentRows = readCsv(confirmedFile);
        assertEquals(3, shipmentRows.size());
        assertEquals(invoice.getId().toString(), value(shipmentRows, 1, "invoice_id"));
        assertEquals(invoice.getNumber(), value(shipmentRows, 1, "invoice_number"));
        assertEquals("'=TRACKING", value(shipmentRows, 1, "tracking_number"));
        assertEquals("'=SHIPMENT-NOTE", value(shipmentRows, 1, "note"));
        assertEquals(0, new BigDecimal("0.10").compareTo(new BigDecimal(value(shipmentRows, 1, "tax_rate"))));
        Path absent = directory.resolve("other-date.csv");
        assertEquals(0, export("export-shipments", absent, 2, shipmentLine - 1, Dates.addDays(day, 1), "CONFIRMED").code);
        assertEquals(1, readCsv(absent).size());
    }

    private static Actor login(String user, String password) {
        AuthenticationResult result = context.getBean("authService", AuthService.class).authenticate(user, password.toCharArray());
        assertTrue(result.getMessage(), result.isAuthenticated());
        return result.getActor();
    }
    private static Customer customer(boolean hold) {
        Customer customer = new Customer();
        customer.setCode(code()); customer.setName("架空CSV得意先"); customer.setAddress("架空県見本市");
        customer.setClosingDay(31); customer.setPaymentTermDays(30); customer.setTaxRounding("DOWN");
        customer.setCreditLimit(new BigDecimal("1000000.00")); customer.setOnHold(hold);
        return catalog.saveCustomer(customer, 0, admin);
    }
    private static Product product(int pack, boolean active) {
        Product product = new Product();
        product.setCode(code()); product.setName("=CSV-PRODUCT"); product.setUnit("個");
        product.setListPrice(new BigDecimal("42.00")); product.setStandardCost(new BigDecimal("20.50"));
        product.setPackSize(pack); product.setActive(active);
        return catalog.saveProduct(product, 0, admin);
    }
    private static List<String> line(String external, Customer customer, String warehouse, Product product, int quantity, String notes) {
        return new ArrayList<String>(Arrays.asList(external, customer.getCode(), warehouse, Dates.format(Dates.today()),
                Dates.format(Dates.today()), external, "", product.getCode(), Integer.toString(quantity), notes));
    }
    @SafeVarargs private final Path csv(List<String>... rows) throws Exception {
        Path path = directory.resolve("input-" + UUID.randomUUID().toString() + ".csv");
        StringWriter text = new StringWriter();
        CsvWriter writer = new CsvWriter(text);
        writer.write(Arrays.asList(ImportFields.ORDERS));
        for (List<String> row : rows) { writer.write(row); }
        writer.flush(); Files.write(path, text.toString().getBytes(UTF8));
        return path;
    }
    private static OrderGroup readGroup(Path file) throws Exception {
        try (OrderCsv csv = new OrderCsv(new CsvReader(Files.newInputStream(file)))) { return csv.read(); }
    }
    private static BatchRun start() {
        String key = key();
        return runs.recordStart(key, "import-orders", "integration fixture", Fingerprints.of(key), null, admin).getRun();
    }
    private static long orderId(String external) throws Exception {
        return count("select order_id from batch_order_import where external_key=?", external);
    }
    private static long count(String sql, Object value) throws Exception {
        try (Connection connection = source.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value);
            try (ResultSet rows = statement.executeQuery()) { assertTrue(rows.next()); return rows.getLong(1); }
        }
    }
    private static String key() { return "order-test-" + UUID.randomUUID().toString(); }
    private static String code() { return "OC" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(java.util.Locale.ROOT); }
    private static Cli importFile(Path input, String runKey) throws Exception {
        return cli("admin", "Demo-admin-2026!", "import-orders", "--file", input.toString(), "--run-key", runKey);
    }
    private static Cli export(String command, Path file, int limit, long after, Date day, String status) throws Exception {
        List<String> args = new ArrayList<String>(Arrays.asList(command, "--output", file.toString(), "--limit",
                Integer.toString(limit), "--after-id", Long.toString(after), "--from", Dates.format(day), "--to", Dates.format(day)));
        if (status != null) { args.add("--status"); args.add(status); }
        return cli("batch", "Demo-batch-2026!", args.toArray(new String[args.size()]));
    }
    private static Cli cli(String user, String password, String... arguments) throws Exception {
        List<String> args = new ArrayList<String>(Arrays.asList(arguments));
        args.addAll(Arrays.asList("--user", user, "--password-env", "ORDER_CSV_TEST_PASSWORD"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int result = BatchMain.run(args.toArray(new String[args.size()]), new PrintStream(out, true, "UTF-8"),
                new PrintStream(err, true, "UTF-8"), Collections.singletonMap("ORDER_CSV_TEST_PASSWORD", password));
        assertFalse(out.toString("UTF-8").contains(password));
        assertFalse(err.toString("UTF-8").contains(password));
        return new Cli(result, out.toString("UTF-8"), err.toString("UTF-8"));
    }
    private static List<List<String>> readCsv(Path file) throws Exception {
        List<List<String>> rows = new ArrayList<List<String>>();
        try (CsvReader reader = new CsvReader(Files.newInputStream(file))) {
            CsvRecord row;
            while ((row = reader.read()) != null) { rows.add(row.getFields()); }
        }
        return rows;
    }
    private static String value(List<List<String>> rows, int row, String header) {
        return rows.get(row).get(rows.get(0).indexOf(header));
    }
    private static final class Cli {
        final int code;
        final String output;
        final String error;
        Cli(int code, String output, String error) { this.code = code; this.output = output; this.error = error; }
    }
}
