package jp.co.tsubame.wholesale.entity;

public class Warehouse extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String code;
    private String name;
    private String address = "";
    private boolean active = true;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
