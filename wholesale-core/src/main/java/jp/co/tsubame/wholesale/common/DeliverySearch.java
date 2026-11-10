package jp.co.tsubame.wholesale.common;

import java.util.Date;

public class DeliverySearch extends Search {
    private Long shipmentId;
    private Date asOfRecordedAt;
    private Date dueOnOrBefore;
    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long id) { shipmentId = id; }
    public Date getAsOfRecordedAt() { return asOfRecordedAt; }
    public void setAsOfRecordedAt(Date at) { asOfRecordedAt = at; }
    public Date getDueOnOrBefore() { return dueOnOrBefore; }
    public void setDueOnOrBefore(Date date) { dueOnOrBefore = Dates.day(date); }
}
