package jp.co.tsubame.wholesale.web.form;

public class TransferForm extends ProductLinesForm {
    private static final long serialVersionUID = 1L;
    private String sourceWarehouseId = "";
    private String destinationWarehouseId = "";
    public String getSourceWarehouseId() { return sourceWarehouseId; }
    public void setSourceWarehouseId(String value) { sourceWarehouseId = value; }
    public String getDestinationWarehouseId() { return destinationWarehouseId; }
    public void setDestinationWarehouseId(String value) { destinationWarehouseId = value; }
}
