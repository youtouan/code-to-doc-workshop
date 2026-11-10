package jp.co.tsubame.wholesale.common;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class QuotationCommand {
    private Long customerId;
    private Long warehouseId;
    private Date quoteDate;
    private Date validUntil;
    private Date requestedDate;
    private String deliveryAddress = "";
    private String externalReference = "";
    private String notes = "";
    private List<QuotationLineCommand> lines = new ArrayList<QuotationLineCommand>();
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long value) { customerId = value; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long value) { warehouseId = value; }
    public Date getQuoteDate() { return quoteDate; }
    public void setQuoteDate(Date value) { quoteDate = value; }
    public Date getValidUntil() { return validUntil; }
    public void setValidUntil(Date value) { validUntil = value; }
    public Date getRequestedDate() { return requestedDate; }
    public void setRequestedDate(Date value) { requestedDate = value; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String value) { deliveryAddress = value; }
    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String value) { externalReference = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public List<QuotationLineCommand> getLines() { return lines; }
    public void setLines(List<QuotationLineCommand> value) { lines = value; }
}
