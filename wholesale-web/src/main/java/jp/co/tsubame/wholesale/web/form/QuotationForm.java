package jp.co.tsubame.wholesale.web.form;

import jp.co.tsubame.wholesale.web.Inputs;

public class QuotationForm extends ProductLinesForm {
    private static final long serialVersionUID = 1L;
    private String quoteDate = "";
    private String validUntil = "";
    private String requestedDate = "";
    private String deliveryAddress = "";
    private String externalReference = "";
    private String notes = "";
    private String saveMode = "save";
    private String revisionNumber = "1";
    private String acceptedOn = "";
    private String customerReference = "";
    private String[] negotiatedUnitPrice = new String[0];
    private String[] negotiationReason = new String[0];
    public String getQuoteDate() { return quoteDate; }
    public void setQuoteDate(String value) { quoteDate = value; }
    public String getValidUntil() { return validUntil; }
    public void setValidUntil(String value) { validUntil = value; }
    public String getRequestedDate() { return requestedDate; }
    public void setRequestedDate(String value) { requestedDate = value; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String value) { deliveryAddress = value; }
    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String value) { externalReference = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public String getSaveMode() { return saveMode; }
    public void setSaveMode(String value) { saveMode = value; }
    public String getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(String value) { revisionNumber = value; }
    public String getAcceptedOn() { return acceptedOn; }
    public void setAcceptedOn(String value) { acceptedOn = value; }
    public String getCustomerReference() { return customerReference; }
    public void setCustomerReference(String value) { customerReference = value; }
    public String[] getNegotiatedUnitPrice() { return negotiatedUnitPrice; }
    public void setNegotiatedUnitPrice(String[] value) { negotiatedUnitPrice = value; }
    public String[] getNegotiationReason() { return negotiationReason; }
    public void setNegotiationReason(String[] value) { negotiationReason = value; }
    public void rows(int count) {
        if (count < 1 || count > 200) { throw Inputs.invalid("見積明細", "見積明細は200行以内です。"); }
        setProductId(grow(getProductId(), count)); setQuantity(grow(getQuantity(), count));
        negotiatedUnitPrice = grow(negotiatedUnitPrice, count); negotiationReason = grow(negotiationReason, count);
    }
}
