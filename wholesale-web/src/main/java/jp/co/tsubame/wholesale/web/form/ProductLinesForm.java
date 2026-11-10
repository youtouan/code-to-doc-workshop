package jp.co.tsubame.wholesale.web.form;

import java.util.Arrays;
import jp.co.tsubame.wholesale.web.Inputs;

public class ProductLinesForm extends BaseForm {
    private static final long serialVersionUID = 1L;
    private String[] productId = new String[0];
    private String[] quantity = new String[0];
    private String note = "";
    private String requestKey = "";
    public String[] getProductId() { return productId; }
    public void setProductId(String[] value) { productId = value; }
    public String[] getQuantity() { return quantity; }
    public void setQuantity(String[] value) { quantity = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public void rows(int count) {
        if (count < 1 || count > Inputs.MAX_LINES) { throw Inputs.invalid("明細", "明細は100行以内です。"); }
        productId = grow(productId, count); quantity = grow(quantity, count);
    }
    protected String[] grow(String[] values, int count) {
        String[] result = Arrays.copyOf(values, count);
        for (int i = 0; i < count; i++) { if (result[i] == null) { result[i] = ""; } }
        return result;
    }
}
