package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import jp.co.tsubame.wholesale.common.Money;

public class CreditMemo extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String number;
    private Customer customer;
    private String customerName;
    private SalesReturn salesReturn;
    private Invoice invoice;
    private String status;
    private Date issuedDate;
    private Date postedDate;
    private String taxRounding;
    private BigDecimal netAmount = Money.ZERO;
    private BigDecimal taxAmount = Money.ZERO;
    private BigDecimal totalAmount = Money.ZERO;
    private BigDecimal appliedAmount = Money.ZERO;
    private String sourceFingerprint;
    private String createdBy;
    private Date createdAt;
    private List<CreditMemoLine> lines = new ArrayList<CreditMemoLine>();

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public SalesReturn getSalesReturn() { return salesReturn; }
    public void setSalesReturn(SalesReturn salesReturn) { this.salesReturn = salesReturn; }
    public Invoice getInvoice() { return invoice; }
    public void setInvoice(Invoice invoice) { this.invoice = invoice; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getIssuedDate() { return issuedDate; }
    public void setIssuedDate(Date issuedDate) { this.issuedDate = issuedDate; }
    public Date getPostedDate() { return postedDate; }
    public void setPostedDate(Date postedDate) { this.postedDate = postedDate; }
    public String getTaxRounding() { return taxRounding; }
    public void setTaxRounding(String taxRounding) { this.taxRounding = taxRounding; }
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getAppliedAmount() { return appliedAmount; }
    public void setAppliedAmount(BigDecimal appliedAmount) { this.appliedAmount = appliedAmount; }
    public BigDecimal getUnappliedAmount() { return totalAmount.subtract(appliedAmount); }
    public String getSourceFingerprint() { return sourceFingerprint; }
    public void setSourceFingerprint(String sourceFingerprint) { this.sourceFingerprint = sourceFingerprint; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public List<CreditMemoLine> getLines() { return lines; }
    public void setLines(List<CreditMemoLine> lines) { this.lines = lines; }
}
