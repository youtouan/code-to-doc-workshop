package jp.co.tsubame.wholesale.web.form;

public class CountForm extends BaseForm {
    private static final long serialVersionUID = 1L;
    private String[] productId = new String[0];
    private String[] lineId = new String[0];
    private String[] countedQuantity = new String[0];
    private String[] lineNote = new String[0];
    private String note = "";
    public String[] getProductId() { return productId; }
    public void setProductId(String[] value) { productId = value; }
    public String[] getLineId() { return lineId; }
    public void setLineId(String[] value) { lineId = value; }
    public String[] getCountedQuantity() { return countedQuantity; }
    public void setCountedQuantity(String[] value) { countedQuantity = value; }
    public String[] getLineNote() { return lineNote; }
    public void setLineNote(String[] value) { lineNote = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
}
