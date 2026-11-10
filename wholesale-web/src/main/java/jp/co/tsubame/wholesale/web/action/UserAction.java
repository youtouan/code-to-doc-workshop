package jp.co.tsubame.wholesale.web.action;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.UserSummary;
import jp.co.tsubame.wholesale.service.UserAdminService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.UserForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class UserAction extends BaseAction {
    protected boolean isMutation(String op) {
        return "create".equals(op) || "update".equals(op) || "unlock".equals(op) || "resetPassword".equals(op);
    }
    private UserAdminService users(HttpServletRequest request) {
        return service(request, "userAdminService", UserAdminService.class);
    }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        UserForm form = (UserForm) base;
        try {
            actor(request).require("ADMIN");
            UserAdminService service = users(request);
            if ("list".equals(form.getOp())) {
                request.setAttribute("statuses", new String[] {"ACTIVE", "DISABLED", "LOCKED"});
                if (form.getStatus().length() != 0) {
                    Inputs.choice(form.getStatus(), "利用状態", "ACTIVE", "DISABLED", "LOCKED");
                }
                request.setAttribute("results", service.searchUsers(Inputs.search(form), actor(request)));
                return view(request, "support/users", "利用者管理");
            }
            if ("new".equals(form.getOp())) { return edit(form, request); }
            if ("edit".equals(form.getOp())) {
                UserSummary user = service.getUser(id(form), actor(request));
                identity(form, user);
                form.setLogin(user.getLogin()); form.setDisplayName(user.getDisplayName());
                form.setSelectedRole(user.getRoles().split(",")); form.setActive(String.valueOf(user.isActive()));
                return edit(form, request);
            }
            if ("detail".equals(form.getOp())) { return detail(form, request, true); }
            UserSummary saved;
            if ("create".equals(form.getOp())) {
                String login = Inputs.text(form.getLogin(), "利用者ID", 50, true);
                String displayName = Inputs.text(form.getDisplayName(), "表示名", 100, true);
                String roles = roleCsv(form.getSelectedRole(), service.listRoles(actor(request)));
                char[] password = password(form);
                try { saved = service.createUser(login, displayName, roles, password, actor(request)); }
                finally { Arrays.fill(password, '\0'); }
            } else if ("update".equals(form.getOp())) {
                saved = service.updateUser(id(form), version(form),
                        Inputs.text(form.getDisplayName(), "表示名", 100, true),
                        roleCsv(form.getSelectedRole(), service.listRoles(actor(request))),
                        Inputs.bool(form.getActive(), "利用状態"), actor(request));
            } else if ("unlock".equals(form.getOp())) {
                saved = service.unlock(id(form), version(form), actor(request));
            } else {
                Long userId = id(form);
                int expectedVersion = version(form);
                char[] password = password(form);
                try { saved = service.resetPassword(userId, expectedVersion, password, actor(request)); }
                finally { Arrays.fill(password, '\0'); }
            }
            return redirect(request, response, "/users.do", saved.getId(), "利用者情報を更新しました。");
        } finally {
            form.clearPasswords();
        }
    }
    protected ActionForward failure(BaseForm base, HttpServletRequest request) {
        UserForm form = (UserForm) base;
        form.clearPasswords();
        if ("list".equals(form.getOp())) { return view(request, "support/users", "利用者検索条件の確認"); }
        if ("create".equals(form.getOp()) || "update".equals(form.getOp())) { return edit(form, request); }
        if (form.getId().length() > 0) { return detail(form, request, false); }
        return view(request, "error", "利用者情報の確認");
    }
    private ActionForward edit(UserForm form, HttpServletRequest request) {
        request.setAttribute("availableRoles", users(request).listRoles(actor(request)));
        request.setAttribute("creatingUser", Boolean.valueOf(form.getId().length() == 0));
        return view(request, "support/user-edit", form.getId().length() == 0 ? "利用者の新規登録" : "利用者情報の編集");
    }
    private ActionForward detail(UserForm form, HttpServletRequest request, boolean refresh) {
        UserSummary user = users(request).getUser(id(form), actor(request));
        request.setAttribute("managedUser", user);
        if (refresh) { identity(form, user); }
        return view(request, "support/user-detail", "利用者詳細 · " + user.getLogin());
    }
    private void identity(UserForm form, UserSummary user) {
        form.setId(String.valueOf(user.getId())); form.setVersion(String.valueOf(user.getVersion()));
    }
    static String roleCsv(String[] selected, List<String> allowed) {
        if (selected == null || selected.length == 0 || selected.length > allowed.size()) {
            throw Inputs.invalid("権限", "権限を1つ以上選択してください。");
        }
        Set<String> unique = new LinkedHashSet<String>();
        for (String role : selected) {
            if (!allowed.contains(role) || !unique.add(role)) {
                throw Inputs.invalid("権限", "不正または重複した権限が指定されています。");
            }
        }
        StringBuilder result = new StringBuilder();
        for (String role : allowed) {
            if (unique.contains(role)) {
                if (result.length() > 0) { result.append(','); }
                result.append(role);
            }
        }
        return result.toString();
    }
    private char[] password(UserForm form) {
        String value = form.getNewPassword();
        if (value == null || value.length() == 0 || value.length() > Inputs.MAX_PASSWORD_LENGTH
                || !value.equals(form.getConfirmation())) {
            throw Inputs.invalid("パスワード", "新しいパスワードと確認入力を確認してください。");
        }
        return value.toCharArray();
    }
}
