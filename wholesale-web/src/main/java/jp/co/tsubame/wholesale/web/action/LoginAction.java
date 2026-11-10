package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import jp.co.tsubame.wholesale.common.AuthenticationResult;
import jp.co.tsubame.wholesale.service.AuthService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.Web;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.LoginForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class LoginAction extends BaseAction {
    protected boolean isRead(String op) { return "list".equals(op); }
    protected boolean isMutation(String op) { return "login".equals(op); }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        LoginForm form = (LoginForm) base;
        if ("list".equals(form.getOp())) {
            if (request.getAttribute("actor") != null) {
                return redirect(request, response, "/dashboard.do", null, "ログイン済みです。");
            }
            return view(request, "login", "ログイン");
        }
        try {
            if (!validate(mapping, form, request)) {
                response.setStatus(422);
                return view(request, "login", "ログイン");
            }
            String login = Inputs.text(form.getLogin(), "利用者ID", 50, true);
            if (form.getPassword().length() > Inputs.MAX_PASSWORD_LENGTH) {
                throw Inputs.invalid("パスワード", "認証情報を確認してください。");
            }
            AuthenticationResult result = service(request, "authService", AuthService.class)
                    .authenticate(login, form.getPassword().toCharArray());
            if (!result.isAuthenticated()) {
                response.setStatus(401);
                Web.error(request, null, result.getMessage());
                return view(request, "login", "ログイン");
            }
            request.getSession().invalidate();
            HttpSession fresh = request.getSession(true);
            fresh.setAttribute(Web.USER_ID, result.getActor().getUserId());
            Web.token(fresh);
            return redirect(request, response, "/dashboard.do", null, "ログインしました。");
        } finally {
            form.setPassword("");
        }
    }
    protected ActionForward failure(BaseForm form, HttpServletRequest request) {
        ((LoginForm) form).setPassword("");
        return view(request, "login", "ログイン");
    }
}
