package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class LogoutAction extends BaseAction {
    protected boolean isRead(String op) { return false; }
    protected boolean isMutation(String op) { return "logout".equals(op); }
    protected ActionForward perform(ActionMapping mapping, BaseForm form, HttpServletRequest request,
                                    HttpServletResponse response) {
        request.getSession().invalidate();
        response.setStatus(303);
        response.setHeader("Location", request.getContextPath() + "/login.do");
        return null;
    }
}
