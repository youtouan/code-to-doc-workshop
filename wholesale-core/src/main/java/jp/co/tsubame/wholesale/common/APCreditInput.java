package jp.co.tsubame.wholesale.common;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class APCreditInput {
    private String supplierCreditNumber;
    private Date creditDate;
    private String reason;
    private List<APCreditLineInput> lines = new ArrayList<APCreditLineInput>();

    public String getSupplierCreditNumber() { return supplierCreditNumber; }
    public void setSupplierCreditNumber(String value) { supplierCreditNumber = value; }
    public Date getCreditDate() { return creditDate; }
    public void setCreditDate(Date value) { creditDate = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public List<APCreditLineInput> getLines() { return lines; }
    public void setLines(List<APCreditLineInput> value) { lines = value; }
}
