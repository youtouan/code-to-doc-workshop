package jp.co.tsubame.wholesale.web.form;

public class AuditForm extends BaseForm {
    private static final long serialVersionUID = 1L;
    private String entityType = "";
    private String entityId = "";
    private String actorLogin = "";
    private String operation = "";
    public String getEntityType() { return entityType; }
    public void setEntityType(String value) { entityType = value; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String value) { entityId = value; }
    public String getActorLogin() { return actorLogin; }
    public void setActorLogin(String value) { actorLogin = value; }
    public String getOperation() { return operation; }
    public void setOperation(String value) { operation = value; }
}
