package jp.co.tsubame.wholesale.batch.entity;

import java.util.Date;
import jp.co.tsubame.wholesale.entity.BaseEntity;
import jp.co.tsubame.wholesale.entity.SalesOrder;

public class OrderImportRecord extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String externalKey;
    private String payloadHash;
    private SalesOrder order;
    private Long createdById;
    private String createdBy;
    private Date createdAt;

    public String getExternalKey() { return externalKey; }
    public void setExternalKey(String value) { externalKey = value; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String value) { payloadHash = value; }
    public SalesOrder getOrder() { return order; }
    public void setOrder(SalesOrder value) { order = value; }
    public Long getCreatedById() { return createdById; }
    public void setCreatedById(Long value) { createdById = value; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { createdBy = value; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date value) { createdAt = value; }
}
