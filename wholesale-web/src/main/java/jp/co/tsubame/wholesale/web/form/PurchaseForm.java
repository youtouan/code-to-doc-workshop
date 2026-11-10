package jp.co.tsubame.wholesale.web.form;

public class PurchaseForm extends ProductLinesForm {
    private static final long serialVersionUID = 1L;
    private String supplierId = "";
    private String orderDate = "";
    private String expectedDate = "";
    private String[] lineExpectedDate = new String[0];
    private String[] lineNote = new String[0];
    public String getSupplierId() { return supplierId; }
    public void setSupplierId(String value) { supplierId = value; }
    public String getOrderDate() { return orderDate; }
    public void setOrderDate(String value) { orderDate = value; }
    public String getExpectedDate() { return expectedDate; }
    public void setExpectedDate(String value) { expectedDate = value; }
    public String[] getLineExpectedDate() { return lineExpectedDate; }
    public void setLineExpectedDate(String[] value) { lineExpectedDate = value; }
    public String[] getLineNote() { return lineNote; }
    public void setLineNote(String[] value) { lineNote = value; }
    public void rows(int count) {
        super.rows(count);
        lineExpectedDate = grow(lineExpectedDate, count);
        lineNote = grow(lineNote, count);
    }
}
