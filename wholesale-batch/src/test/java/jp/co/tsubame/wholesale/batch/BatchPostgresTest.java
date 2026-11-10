package jp.co.tsubame.wholesale.batch;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import jp.co.tsubame.wholesale.batch.csv.CsvReader;
import jp.co.tsubame.wholesale.batch.entity.BatchRow;
import jp.co.tsubame.wholesale.batch.entity.BatchRun;
import jp.co.tsubame.wholesale.batch.service.BatchRowService;
import jp.co.tsubame.wholesale.batch.service.BatchRunService;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.AuthenticationResult;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Fingerprints;
import jp.co.tsubame.wholesale.common.OrderInput;
import jp.co.tsubame.wholesale.common.OrderLineInput;
import jp.co.tsubame.wholesale.common.ShipmentLineInput;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.service.AuthService;
import jp.co.tsubame.wholesale.service.CatalogService;
import jp.co.tsubame.wholesale.service.InventoryService;
import jp.co.tsubame.wholesale.service.OrderService;
import jp.co.tsubame.wholesale.service.ShippingService;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.Assert.*;

/** Opt-in real PostgreSQL tests; never create/reset schemas or run against production database names. */
public class BatchPostgresTest {
    private static ClassPathXmlApplicationContext context;
    private static Actor batch;
    private static Actor admin;
    private static Actor sales;
    private static BatchRunService runs;
    private static BatchRowService worker;
    private static BatchOrchestrator orchestrator;
    private static DataSource source;
    private Path directory;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    @BeforeClass public static void connect() throws Exception {
        Assume.assumeTrue("Enable with -Ddb.tests=true after applying all SQL to wholesale_test",
                Boolean.getBoolean("db.tests"));
        context = new ClassPathXmlApplicationContext("application-context.xml");
        source = context.getBean("dataSource", DataSource.class);
        try (Connection connection = source.getConnection()) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
            String name = connection.getCatalog();
            assertTrue("Refusing fixtures outside a dedicated wholesale_test database",
                    "wholesale_test".equals(name) || name.startsWith("wholesale_test_"));
        }
        batch = login("batch");
        admin = login("admin");
        sales = login("sales");
        runs = context.getBean("batchRunService", BatchRunService.class);
        worker = context.getBean("batchRowService", BatchRowService.class);
        orchestrator = context.getBean("batchOrchestrator", BatchOrchestrator.class);
    }

    @AfterClass public static void disconnect() {
        if (context != null) { context.close(); context = null; }
    }

    @Before public void prepareFiles() throws Exception {
        directory = Paths.get("target", "batch-pg-" + UUID.randomUUID().toString());
        Files.createDirectories(directory);
    }

    @After public void removeFiles() throws Exception {
        if (directory != null) {
            try (DirectoryStream<Path> paths = Files.newDirectoryStream(directory)) {
                for (Path path : paths) { Files.deleteIfExists(path); }
            }
            Files.delete(directory);
        }
    }

    @Test public void proxiesAndHealthUseRealDatabase() throws Exception {
        assertTrue(AopUtils.isAopProxy(runs));
        assertTrue(AopUtils.isAopProxy(worker));
        assertFalse(AopUtils.isAopProxy(orchestrator));
        assertEquals(0, execute("health"));
    }

    @Test public void partialImportRecordsRollbackAndTerminalRerunIsImmutable() throws Exception {
        String code = code();
        String bad = code();
        String other = code();
        String key = key();
        Path input = productFile(productLine(code, "1") + productLine(bad, "0") + productLine(other, "1"));
        assertEquals(3, execute("import-products", "--file", input.toString(), "--run-key", key));
        BatchRun run = runs.getRun(key, batch);
        assertEquals("PARTIAL", run.getStatus());
        assertEquals(3, run.getTotalRows());
        assertEquals(2, run.getSuccessRows());
        assertEquals(1, run.getRejectedRows());
        assertNotNull(run.getEndedAt());
        assertEquals(64, run.getInputSha256().length());
        List<BatchRow> rows = runs.listRows(run.getId(), 0, 10, batch);
        assertEquals("SUCCESS", rows.get(0).getResult());
        assertEquals("REJECTED", rows.get(1).getResult());
        assertEquals(3, rows.get(1).getSourceLine());
        assertEquals(0, number("select count(*) from product where code=?", bad));
        long version = number("select version from product where code=?", code);
        assertEquals(3, execute("import-products", "--file", input.toString(), "--run-key", key));
        assertEquals(version, number("select version from product where code=?", code));
        assertEquals(3, runs.listRows(run.getId(), 0, 10, batch).size());
        Files.write(input, (header(ImportFields.PRODUCTS) + productLine(code, "2")).getBytes(UTF8));
        try {
            execute("import-products", "--file", input.toString(), "--run-key", key);
            fail("Same key must reject changed file");
        } catch (BusinessException expected) { assertEquals("batch.keyConflict", expected.getCode()); }
    }

    @Test public void batchMainAuthenticatesImportsReplaysAndReportsDurableResults() throws Exception {
        String first = code();
        String rejected = code();
        String second = code();
        String key = key();
        Path input = productFile(productLine(first, "1") + productLine(rejected, "0") + productLine(second, "1"));
        CliResult imported = runMain("import-products", "--file", input.toString(), "--run-key", key);
        assertEquals(imported.error, 3, imported.code);
        assertTrue(imported.output.contains("status=PARTIAL"));
        assertTrue(imported.output.contains("total=3 success=2 rejected=1"));
        BatchRun run = runs.getRun(key, batch);
        assertEquals(2, run.getSuccessRows());
        assertEquals(1, run.getRejectedRows());
        assertFalse(run.getInvocation().contains("Demo-batch-2026!"));
        long version = number("select version from product where code=?", first);
        CliResult replayed = runMain("import-products", "--file", input.toString(), "--run-key", key);
        assertEquals(3, replayed.code);
        assertTrue(replayed.output.contains("replayed=true"));
        assertEquals(version, number("select version from product where code=?", first));
        CliResult reported = runMain("row-results", "--run-key", key, "--limit", "10");
        assertEquals(reported.error, 0, reported.code);
        try (CsvReader csv = new CsvReader(new java.io.ByteArrayInputStream(reported.output.getBytes(UTF8)))) {
            assertEquals("row_number", csv.read().getFields().get(0));
            assertEquals("SUCCESS", csv.read().getFields().get(2));
            assertEquals("REJECTED", csv.read().getFields().get(2));
            assertEquals("SUCCESS", csv.read().getFields().get(2));
            assertNull(csv.read());
        }
        assertTrue(reported.error.contains("run_status=PARTIAL"));
        Files.write(input, (header(ImportFields.PRODUCTS) + productLine(first, "2")).getBytes(UTF8));
        CliResult conflicting = runMain("import-products", "--file", input.toString(), "--run-key", key);
        assertEquals(2, conflicting.code);
        assertTrue(conflicting.error.contains("batch.keyConflict"));
        assertEquals(0, number("select count(*) from product where code=?", rejected));
    }

    @Test public void successAndResultSurviveOuterTransactionRollback() throws Exception {
        final String key = key();
        final String code = code();
        TransactionTemplate outer = new TransactionTemplate(context.getBean("transactionManager", PlatformTransactionManager.class));
        outer.execute(new TransactionCallback<Object>() {
            public Object doInTransaction(TransactionStatus status) {
                BatchRun run = start(key, "import-products");
                worker.executeRow(run.getId(), productTask(code, 1), batch);
                status.setRollbackOnly();
                return null;
            }
        });
        BatchRun run = runs.getRun(key, batch);
        assertEquals(1, number("select count(*) from product where code=?", code));
        assertEquals(1, run.getSuccessRows());
        worker.executeRow(run.getId(), productTask(code, 1), batch);
        assertEquals(1, runs.getRun(key, batch).getSuccessRows());
        runs.recordComplete(run.getId(), false, "test completed", batch);
    }

    @Test public void resultInsertFailureRollsBackBusinessMutationAndAuditTogether() throws Exception {
        String code = code();
        BatchRun run = start(key(), "import-products");
        try {
            // Invalid row_number forces PostgreSQL to reject the result AFTER CatalogService saved/flushed.
            worker.executeRow(run.getId(), productTask(code, 0), batch);
            fail("Expected batch result constraint violation");
        } catch (RuntimeException expected) {
            assertFalse(expected instanceof BusinessException);
        }
        assertEquals(0, number("select count(*) from product where code=?", code));
        assertEquals(0, number("select count(*) from audit_event where detail=?", code));
        assertEquals(0, runs.getRun(run.getRunKey(), batch).getTotalRows());
        runs.recordRejected(run.getId(), productTask(code, 1), "test.rollback", "Failure recorded after rollback", batch);
        runs.recordFailed(run.getId(), "Deliberate result persistence failure test", 1, batch);
        assertEquals(1, runs.getRun(run.getRunKey(), batch).getRejectedRows());
    }

    @Test public void runningCrashStateIsNeverReplayedOrClaimedComplete() throws Exception {
        String key = key();
        BatchRun run = start(key, "import-products");
        try {
            runs.recordStart(key, "import-products", "test", Fingerprints.of(key), null, batch);
            fail("RUNNING must refuse replay");
        } catch (BusinessException expected) { assertEquals("batch.running", expected.getCode()); }
        assertEquals("RUNNING", runs.getRun(key, batch).getStatus());
        assertNull(runs.getRun(key, batch).getEndedAt());
        runs.recordFailed(run.getId(), "Crash policy test concluded", 1, batch);
        StartResult replay = runs.recordStart(key, "import-products", "test", Fingerprints.of(key), null, batch);
        assertFalse(replay.isCreated());
        assertEquals(1, replay.getRun().getExitCode());
    }

    @Test public void receiptRequestKeyProtectsAcrossDifferentBatchKeys() throws Exception {
        Product product = product();
        String request = key();
        String key = key();
        Path input = directory.resolve("receipts.csv");
        String line = request + ",EAST," + product.getCode() + ",7,50.00," + Dates.format(Dates.today()) + ",TEST,example\n";
        Files.write(input, (header(ImportFields.RECEIPTS) + line).getBytes(UTF8));
        assertEquals(0, runMain("import-receipts", "--file", input.toString(), "--run-key", key).code);
        assertEquals(0, execute("import-receipts", "--file", input.toString(), "--run-key", key));
        assertEquals(0, execute("import-receipts", "--file", input.toString(), "--run-key", key()));
        assertEquals(1, number("select count(*) from stock_receipt where request_key=?", request));
        assertEquals(7, number("select on_hand from stock_balance where warehouse_id=21 and product_id=?", product.getId()));
        Files.write(input, (header(ImportFields.RECEIPTS) + line.replace(",7,", ",8,")).getBytes(UTF8));
        assertEquals(3, execute("import-receipts", "--file", input.toString(), "--run-key", key()));
        assertEquals(7, number("select on_hand from stock_balance where warehouse_id=21 and product_id=?", product.getId()));
    }

    @Test public void malformedCsvFailsJobButKeepsAlreadyCommittedRows() throws Exception {
        String code = code();
        String key = key();
        Path input = productFile(productLine(code, "1") + "\"unterminated");
        CliResult malformed = runMain("import-products", "--file", input.toString(), "--run-key", key);
        assertEquals(3, malformed.code);
        assertTrue(malformed.error.contains("unterminated"));
        BatchRun run = runs.getRun(key, batch);
        assertEquals("FAILED", run.getStatus());
        assertEquals(3, run.getExitCode());
        assertEquals(1, run.getSuccessRows());
        assertEquals(1, run.getRejectedRows());
        assertEquals(1, number("select count(*) from product where code=?", code));
        assertEquals(3, execute("import-products", "--file", input.toString(), "--run-key", key));
    }

    @Test public void allocationRecordsActualShortageWithoutRetryLoop() throws Exception {
        Product product = product();
        Customer customer = customer();
        Date end = Dates.monthEnd(Dates.addMonths(Dates.today(), -1));
        SalesOrder order = order(customer, product, 9, end);
        String key = key();
        CliResult allocated = runMain("daily-allocation", "--through", Dates.format(end), "--limit", "1000", "--run-key", key);
        assertEquals(allocated.error, 0, allocated.code);
        BatchRun run = runs.getRun(key, batch);
        BatchRow matched = null;
        for (BatchRow row : runs.listRows(run.getId(), 0, 1000, batch)) {
            if (order.getId().equals(row.getEntityId())) { matched = row; }
        }
        assertNotNull(matched);
        assertEquals("SUCCESS", matched.getResult());
        assertEquals("allocation.shortage", matched.getCode());
        assertTrue(matched.getMessage().contains("shortage=9"));
        assertTrue(run.getTotalRows() <= 1000);
    }

    @Test public void monthlyBillingFinalizesThroughCoreServicesAndExportsSafely() throws Exception {
        Product product = product();
        Customer customer = customer();
        Date end = Dates.monthEnd(Dates.addMonths(Dates.today(), -1));
        context.getBean("inventoryService", InventoryService.class).receive(key(), 21L, product.getId(),
                4, new BigDecimal("50.00"), end, "TEST", "", batch);
        SalesOrder order = order(customer, product, 4, end);
        OrderService orders = context.getBean("orderService", OrderService.class);
        order = orders.allocate(order.getId(), order.getVersion(), batch);
        ShipmentLineInput line = new ShipmentLineInput();
        line.setOrderLineId(order.getLines().get(0).getId());
        line.setQuantity(4);
        ShippingService shipping = context.getBean("shippingService", ShippingService.class);
        Shipment shipment = shipping.instruct(order.getId(), order.getVersion(), end, "OWN", "", Arrays.asList(line), admin);
        shipping.confirm(shipment.getId(), shipment.getVersion(), end, "TEST", admin);
        String key = key();
        CliResult billed = runMain("monthly-billing", "--period-end", Dates.format(end), "--limit", "1000", "--run-key", key, "--finalize");
        assertEquals(billed.error, 0, billed.code);
        assertEquals(1, number("select count(*) from billing_invoice where customer_id=? and status='FINALIZED'", customer.getId()));
        CliResult replayed = runMain("monthly-billing", "--period-end", Dates.format(end), "--limit", "1000", "--run-key", key, "--finalize");
        assertEquals(replayed.error, 0, replayed.code);
        assertTrue(replayed.output.contains("replayed=true"));
        assertEquals(1, number("select count(*) from billing_invoice where customer_id=?", customer.getId()));
        for (String command : Arrays.asList("export-stock", "export-orders", "export-invoices")) {
            Path output = directory.resolve(command + ".csv");
            CliResult exported = runMain(command, "--output", output.toString(), "--limit", "100000");
            assertEquals(exported.error, 0, exported.code);
            try (CsvReader reader = new CsvReader(Files.newInputStream(output))) {
                assertEquals("id", reader.read().getFields().get(0));
                assertNotNull(reader.read());
            }
            assertEquals(2, runMain(command, "--output", output.toString(), "--limit", "10").code);
        }
    }

    private static Actor login(String login) {
        AuthenticationResult result = context.getBean("authService", AuthService.class)
                .authenticate(login, ("Demo-" + login + "-2026!").toCharArray());
        assertTrue("Seed account authentication failed: " + login, result.isAuthenticated());
        return result.getActor();
    }

    private static BatchRun start(String key, String command) {
        return runs.recordStart(key, command, "test", Fingerprints.of(key), null, batch).getRun();
    }

    private int execute(String... args) throws Exception {
        return orchestrator.execute(Arguments.parse(authenticatedArguments(args)), batch,
                new PrintStream(new ByteArrayOutputStream(), true, "UTF-8"),
                new PrintStream(new ByteArrayOutputStream(), true, "UTF-8"));
    }

    private static String[] authenticatedArguments(String[] args) {
        String[] all = Arrays.copyOf(args, args.length + 4);
        all[args.length] = "--user";
        all[args.length + 1] = "batch";
        all[args.length + 2] = "--password-env";
        all[args.length + 3] = "WHOLESALE_BATCH_PASSWORD";
        return all;
    }

    private static CliResult runMain(String... args) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        int code = BatchMain.run(authenticatedArguments(args), new PrintStream(output, true, "UTF-8"),
                new PrintStream(error, true, "UTF-8"),
                java.util.Collections.singletonMap("WHOLESALE_BATCH_PASSWORD", "Demo-batch-2026!"));
        CliResult result = new CliResult(code, output.toString("UTF-8"), error.toString("UTF-8"));
        assertFalse(result.output.contains("Demo-batch-2026!"));
        assertFalse(result.error.contains("Demo-batch-2026!"));
        return result;
    }

    private static final class CliResult {
        private final int code;
        private final String output;
        private final String error;
        private CliResult(int code, String output, String error) {
            this.code = code;
            this.output = output;
            this.error = error;
        }
    }

    private Path productFile(String lines) throws Exception {
        Path file = directory.resolve("products.csv");
        Files.write(file, (header(ImportFields.PRODUCTS) + lines).getBytes(UTF8));
        return file;
    }

    private static String productLine(String code, String pack) {
        return code + ",架空試験商品,個,STANDARD,100.00,50.00," + pack + ",0,0,true,unit-test\n";
    }

    private static RowTask productTask(String code, int number) {
        return new RowTask("import-products", number, 2, Arrays.asList(code, "架空試験商品", "個", "STANDARD",
                "100.00", "50.00", "1", "0", "0", "true", "unit-test"));
    }

    private static String header(String[] fields) {
        StringBuilder result = new StringBuilder();
        for (String field : fields) { if (result.length() > 0) { result.append(','); } result.append(field); }
        return result.append('\n').toString();
    }

    private static String code() {
        return "BP" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(java.util.Locale.ROOT);
    }
    private static String key() { return "batch-test-" + UUID.randomUUID().toString(); }

    private static long number(String sql, Object parameter) throws Exception {
        try (Connection connection = source.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, parameter);
            try (ResultSet result = statement.executeQuery()) { assertTrue(result.next()); return result.getLong(1); }
        }
    }

    private static Product product() {
        Product input = new Product();
        input.setCode(code());
        input.setName("=架空試験商品");
        input.setListPrice(new BigDecimal("100.00"));
        input.setStandardCost(new BigDecimal("50.00"));
        return context.getBean("catalogService", CatalogService.class).saveProduct(input, 0, batch);
    }

    private static Customer customer() {
        Customer input = new Customer();
        input.setCode(code());
        input.setName("架空バッチ試験商店");
        input.setCreditLimit(new BigDecimal("1000000.00"));
        input.setClosingDay(31);
        input.setPaymentTermDays(30);
        input.setTaxRounding("DOWN");
        input.setAddress("架空県試験市");
        return context.getBean("catalogService", CatalogService.class).saveCustomer(input, 0, admin);
    }

    private static SalesOrder order(Customer customer, Product product, int quantity, Date date) {
        OrderInput input = new OrderInput();
        input.setCustomerId(customer.getId());
        input.setWarehouseId(21L);
        input.setOrderDate(date);
        input.setRequestedDate(date);
        input.setDeliveryAddress("架空県試験市");
        OrderLineInput line = new OrderLineInput();
        line.setProductId(product.getId());
        line.setQuantity(quantity);
        input.getLines().add(line);
        OrderService orders = context.getBean("orderService", OrderService.class);
        SalesOrder order = orders.saveDraft(null, 0, input, sales);
        order = orders.submit(order.getId(), order.getVersion(), sales);
        return orders.approve(order.getId(), order.getVersion(), admin);
    }
}
