package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.service.AuthService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.Web;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.PasswordForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class PasswordAction extends BaseAction {
    protected boolean isRead(String op) { return "list".equals(op); }
    protected boolean isMutation(String op) { return "change".equals(op); }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        PasswordForm form = (PasswordForm) base;
        if ("list".equals(form.getOp())) { return view(request, "password", "パスワード変更"); }
        try {
            if (!validate(mapping, form, request)) {
                response.setStatus(422);
                return view(request, "password", "パスワード変更");
            }
            if (!form.getNewPassword().equals(form.getConfirmation()) || form.getNewPassword().length() > Inputs.MAX_PASSWORD_LENGTH
                    || form.getCurrentPassword().length() > Inputs.MAX_PASSWORD_LENGTH) {
                throw Inputs.invalid("確認", "新しいパスワードと確認入力を確認してください。");
            }
            service(request, "authService", AuthService.class).changePassword(
                    form.getCurrentPassword().toCharArray(), form.getNewPassword().toCharArray(), actor(request));
            request.getSession().setAttribute(Web.TOKEN, Web.randomToken());
            return redirect(request, response, "/dashboard.do", null, "パスワードを変更しました。");
        } finally {
            form.clear();
        }
    }
    protected ActionForward failure(BaseForm form, HttpServletRequest request) {
        ((PasswordForm) form).clear();
        return view(request, "password", "パスワード変更");
    }
}
