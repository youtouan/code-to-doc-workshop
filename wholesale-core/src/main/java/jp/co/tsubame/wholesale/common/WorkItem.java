package jp.co.tsubame.wholesale.common;

import java.util.Date;

public final class WorkItem {
    private final String type;
    private final Long id;
    private final String number;
    private final String party;
    private final Date dueDate;
    private final String status;

    public WorkItem(String type, Long id, String number, String party, Date dueDate, String status) {
        this.type = type;
        this.id = id;
        this.number = number;
        this.party = party;
        this.dueDate = dueDate;
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public Long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public String getParty() {
        return party;
    }

    public Date getDueDate() {
        return dueDate;
    }

    public String getStatus() {
        return status;
    }

    public boolean isOverdue() {
        return dueDate != null && dueDate.before(Dates.today());
    }
}
