package jp.co.tsubame.wholesale.entity;

import java.util.Date;

public class StockMovement extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private StockBalance balance;
    private String movementType;
    private int quantityChange;
    private int reservedChange;
    private int onHandAfter;
    private int reservedAfter;
    private String documentType;
    private Long documentId;
    private String documentNumber;
    private String actor;
    private String note;
    private Date occurredAt;

    public StockBalance getBalance() {
        return balance;
    }

    public void setBalance(StockBalance balance) {
        this.balance = balance;
    }

    public String getMovementType() {
        return movementType;
    }

    public void setMovementType(String movementType) {
        this.movementType = movementType;
    }

    public int getQuantityChange() {
        return quantityChange;
    }

    public void setQuantityChange(int quantityChange) {
        this.quantityChange = quantityChange;
    }

    public int getReservedChange() {
        return reservedChange;
    }

    public void setReservedChange(int reservedChange) {
        this.reservedChange = reservedChange;
    }

    public int getOnHandAfter() {
        return onHandAfter;
    }

    public void setOnHandAfter(int onHandAfter) {
        this.onHandAfter = onHandAfter;
    }

    public int getReservedAfter() {
        return reservedAfter;
    }

    public void setReservedAfter(int reservedAfter) {
        this.reservedAfter = reservedAfter;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public void setDocumentNumber(String documentNumber) {
        this.documentNumber = documentNumber;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Date getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Date occurredAt) {
        this.occurredAt = occurredAt;
    }
}
