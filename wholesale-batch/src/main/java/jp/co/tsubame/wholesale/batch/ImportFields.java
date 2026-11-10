package jp.co.tsubame.wholesale.batch;

import java.math.BigDecimal;
import java.util.List;
import jp.co.tsubame.wholesale.common.BusinessException;

public final class ImportFields {
    public static final String[] PRODUCTS = { "code", "name", "unit", "tax_category", "list_price",
        "standard_cost", "pack_size", "reorder_point", "reorder_quantity", "active", "notes" };
    public static final String[] RECEIPTS = { "request_key", "warehouse_code", "product_code", "quantity",
        "unit_cost", "receipt_date", "reference", "note" };
    public static final String[] ORDERS = { "external_key", "customer_code", "warehouse_code", "order_date",
        "requested_date", "external_reference", "delivery_address", "product_code", "quantity", "notes" };

    private ImportFields() { }
    public static String[] header(String command) {
        if ("import-products".equals(command)) { return PRODUCTS.clone(); }
        if ("import-receipts".equals(command)) { return RECEIPTS.clone(); }
        if ("import-orders".equals(command)) { return ORDERS.clone(); }
        throw new IllegalArgumentException("Not an import command");
    }
    public static void count(List<String> fields, int expected) {
        if (fields.size() != expected) {
            throw new BusinessException("csv.columns", "Expected " + expected + " columns but received " + fields.size());
        }
    }
    public static int integer(String value, String field) {
        try {
            if (!value.matches("-?[0-9]+")) { throw new NumberFormatException(); }
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException("csv.integer", field + " must be a whole number within 32-bit range");
        }
    }
    public static BigDecimal money(String value, String field) {
        if (!value.matches("-?[0-9]{1,16}(\\.[0-9]{1,2})?")) {
            throw new BusinessException("csv.money", field + " must be decimal with at most 16 integer and 2 fractional digits");
        }
        return new BigDecimal(value);
    }
    public static boolean bool(String value, String field) {
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new BusinessException("csv.boolean", field + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }
}
