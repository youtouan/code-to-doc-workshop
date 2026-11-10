package jp.co.tsubame.wholesale.web.form;

public class LoginForm extends BaseForm {
    private static final long serialVersionUID = 1L;
    private String login = "";
    private String password = "";
    public String getLogin() { return login; }
    public void setLogin(String value) { login = value; }
    public String getPassword() { return password; }
    public void setPassword(String value) { password = value; }
}
