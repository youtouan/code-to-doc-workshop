package jp.co.tsubame.wholesale.web.form;

public class PasswordForm extends BaseForm {
    private static final long serialVersionUID = 1L;
    private String currentPassword = "";
    private String newPassword = "";
    private String confirmation = "";
    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String value) { currentPassword = value; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String value) { newPassword = value; }
    public String getConfirmation() { return confirmation; }
    public void setConfirmation(String value) { confirmation = value; }
    public void clear() { currentPassword = ""; newPassword = ""; confirmation = ""; }
}
