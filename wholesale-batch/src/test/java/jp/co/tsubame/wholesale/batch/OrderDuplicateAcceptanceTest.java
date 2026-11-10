package jp.co.tsubame.wholesale.batch;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import jp.co.tsubame.wholesale.batch.csv.CsvReader;
import jp.co.tsubame.wholesale.common.BusinessException;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * ACC-001: ADR-0001（docs/adr/0001-order-import-validation.md）から作る To-Be 受け入れテスト。
 *
 * 2-2: ADR-0001 の決定から下の EXPECTED_* に期待するメッセージを書き、2つの @Ignore の行を消す。
 *      ISSUE-001 を実装する前に実行し、ACC-001-1 と ACC-001-2 が失敗する（赤）ことを確かめる。
 * 3-1: ISSUE-001 を実装し、3件とも成功する（緑）ことを確かめる。CHAR-ORD-001 は緑のまま保つ。
 *
 * データベースは使わない。OrderCsv で CSV を読み、OrderGroup.validate() を呼ぶ。
 * 取り込みでは OrderImportService.importDraft() が最初にこのメソッドを呼び、例外ならグループ全体を却下する。
 * 受注が作られないことと終了コードは CHAR-ORD-001 が確かめるので、ここでは確かめない。
 *
 * 実行: mvn -B -ntp -pl wholesale-batch -am test -Dtest=OrderDuplicateAcceptanceTest -Dsurefire.failIfNoSpecifiedTests=false
 */
public class OrderDuplicateAcceptanceTest {
    static final String PENDING = "ACC-001: 2-2 で ADR-0001 から期待値を書く";
    private static final String DUPLICATE_PRODUCT = "orderImport.duplicateProduct";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String HEADER = "external_key,customer_code,warehouse_code,order_date,requested_date,"
            + "external_reference,delivery_address,product_code,quantity,notes\n";

    // 2-2: ADR-0001 で決めた形式で、却下理由のメッセージ全体を書く。
    // ACC-001-1: samples/orders-rejections.csv の CSV-ORDER-DUPLICATE（P001 がデータの5行目と6行目にある）
    private static final String EXPECTED_MESSAGE_ROWS_5_6 = "";
    // ACC-001-2: 同じグループで P001 がデータの1・3・4行目にある（acc001_2 の CSV を参照）
    private static final String EXPECTED_MESSAGE_ROWS_1_3_4 = "";

    /**
     * ACC-001-1
     * Given: samples/orders-rejections.csv（CSV-ORDER-DUPLICATE のグループで、P001 がデータの5行目と6行目にある）
     * When:  そのグループを検証する
     * Then:  エラーコードは orderImport.duplicateProduct、メッセージは EXPECTED_MESSAGE_ROWS_5_6 と一致する
     */
    @Ignore(PENDING)
    @Test public void acc001_1_sampleDuplicateListsBothSourceRows() throws Exception {
        BusinessException rejected = rejectionOf(sampleGroup("CSV-ORDER-DUPLICATE"));
        assertRejectedWith(EXPECTED_MESSAGE_ROWS_5_6, rejected);
    }

    /**
     * ACC-001-2
     * Given: 同じグループで P001 がデータの1・3・4行目に、P002 が2行目にある CSV
     * When:  そのグループを検証する
     * Then:  エラーコードは orderImport.duplicateProduct、メッセージは EXPECTED_MESSAGE_ROWS_1_3_4 と一致する
     */
    @Ignore(PENDING)
    @Test public void acc001_2_tripleOccurrenceListsEverySourceRow() throws Exception {
        OrderGroup group = group(line("DUP", "P001", "1") + line("DUP", "P002", "1")
                + line("DUP", "P001", "2") + line("DUP", "P001", "3"));
        assertRejectedWith(EXPECTED_MESSAGE_ROWS_1_3_4, rejectionOf(group));
    }

    /**
     * ACC-001-3（期待値が要らないので、最初から実行される）
     * Given: 同じグループで商品コードが重複しない CSV
     * When:  そのグループを検証する
     * Then:  例外は発生しない
     */
    @Test public void acc001_3_groupWithoutDuplicatesIsStillAccepted() throws Exception {
        group(line("OK", "P001", "1") + line("OK", "P002", "1")).validate();
    }

    private static void assertRejectedWith(String expectedMessage, BusinessException rejected) {
        assertFalse("ACC-001: 期待値が空です。ADR-0001 の決定から書いてください", expectedMessage.isEmpty());
        assertEquals(DUPLICATE_PRODUCT, rejected.getCode());
        assertEquals(expectedMessage, rejected.getMessage());
    }

    private static BusinessException rejectionOf(OrderGroup group) {
        try {
            group.validate();
        } catch (BusinessException rejected) {
            return rejected;
        }
        fail("Group " + group.getKey() + " was accepted");
        return null;
    }

    /** Reads samples/orders-rejections.csv from the repository root or from the module directory. */
    private static OrderGroup sampleGroup(String key) throws Exception {
        File file = new File("samples", "orders-rejections.csv");
        if (!file.isFile()) { file = new File(new File("..", "samples"), "orders-rejections.csv"); }
        try (OrderCsv reader = new OrderCsv(new CsvReader(new FileInputStream(file)))) {
            for (OrderGroup group = reader.read(); group != null; group = reader.read()) {
                if (key.equals(group.getKey())) { return group; }
            }
        }
        fail("Group " + key + " not found in " + file.getPath());
        return null;
    }

    private static OrderGroup group(String lines) throws Exception {
        try (OrderCsv reader = new OrderCsv(new CsvReader(new ByteArrayInputStream((HEADER + lines).getBytes(UTF8))))) {
            return reader.read();
        }
    }

    private static String line(String key, String product, String quantity) {
        return key + ",C001,EAST,2026-09-01,2026-09-08,PO-" + key + ",," + product + "," + quantity + ",\n";
    }
}
