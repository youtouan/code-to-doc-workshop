package jp.co.tsubame.wholesale.web.form;

public class UserForm extends BaseForm {
    private static final long serialVersionUID = 1L;
    private String login = "";
    private String displayName = "";
    private String[] selectedRole = new String[0];
    private String active = "true";
    private String newPassword = "";
    private String confirmation = "";
    public String getLogin() { return login; }
    public void setLogin(String value) { login = value; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { displayName = value; }
    public String[] getSelectedRole() { return selectedRole; }
    public void setSelectedRole(String[] value) { selectedRole = value; }
    public String getActive() { return active; }
    public void setActive(String value) { active = value; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String value) { newPassword = value; }
    public String getConfirmation() { return confirmation; }
    public void setConfirmation(String value) { confirmation = value; }
    public void clearPasswords() { newPassword = ""; confirmation = ""; }
}
