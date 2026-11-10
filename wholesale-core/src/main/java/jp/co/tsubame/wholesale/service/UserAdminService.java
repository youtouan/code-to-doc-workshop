package jp.co.tsubame.wholesale.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.Passwords;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.common.UserSummary;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.AppUser;

public class UserAdminService extends BaseService {
    private static final Set<String> ALLOWED_ROLES = new LinkedHashSet<String>(
            Arrays.asList("SALES", "WAREHOUSE", "BILLING", "MANAGER", "BATCH", "ADMIN"));

    public Page<UserSummary> searchUsers(Search search, Actor actor) {
        require(actor, "ADMIN");
        Map<String, Object> parameters = WholesaleDao.params("text", search.getLikeText());
        String where = " from AppUser u where (u.login like :text escape '!' or u.displayName like :text escape '!')";
        if ("ACTIVE".equals(search.getStatus())) {
            where += " and u.active=true";
        } else if ("DISABLED".equals(search.getStatus())) {
            where += " and u.active=false";
        } else if ("LOCKED".equals(search.getStatus())) {
            where += " and u.lockedUntil>:now";
            parameters.put("now", new Date());
        }
        Page<AppUser> page = dao.page("select u" + where + " order by u.login",
                "select count(u.id)" + where, parameters, search);
        List<UserSummary> users = new ArrayList<UserSummary>();
        for (AppUser user : page.getItems()) {
            users.add(new UserSummary(user));
        }
        return new Page<UserSummary>(users, page.getTotal(), page.getNumber(), page.getSize());
    }

    public UserSummary getUser(Long id, Actor actor) {
        require(actor, "ADMIN");
        return new UserSummary(dao.get(AppUser.class, id));
    }

    public List<String> listRoles(Actor actor) {
        require(actor, "ADMIN");
        return new ArrayList<String>(ALLOWED_ROLES);
    }

    public UserSummary createUser(String login, String displayName, String roleCsv, char[] password, Actor actor) {
        require(actor, "ADMIN");
        try {
            String normalized = Checks.text(login, "利用者ID", 50).toLowerCase(Locale.ROOT);
            Checks.state(normalized.matches("[a-z][a-z0-9._-]{2,49}"), "user.login",
                    "利用者IDは英字から始まる3～50文字の英小文字・数字・記号._-で入力してください。");
            String roles = roles(roleCsv);
            Passwords.validateNew(password);
            dao.lockKey("users", "administration");
            Checks.state(dao.count("select count(u.id) from AppUser u where u.login=:login",
                    WholesaleDao.params("login", normalized)) == 0, "user.duplicate", "同じ利用者IDが登録されています。");
            AppUser user = new AppUser();
            user.setLogin(normalized);
            user.setDisplayName(Checks.text(displayName, "表示名", 100));
            user.setRoles(roles);
            user.setActive(true);
            user.setPasswordHash(Passwords.hash(password));
            user.setPasswordChangedAt(new Date());
            dao.save(user);
            audit(actor, "USER_CREATE", user, normalized + " roles=" + roles);
            dao.flush();
            return new UserSummary(user);
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    public UserSummary updateUser(Long id, int expectedVersion, String displayName, String roleCsv,
                                  boolean active, Actor actor) {
        require(actor, "ADMIN");
        String roles = roles(roleCsv);
        dao.lockKey("users", "administration");
        AppUser user = dao.lock(AppUser.class, id);
        Checks.version(user.getVersion(), expectedVersion);
        if (user.getId().equals(actor.getUserId())) {
            Checks.state(active && Arrays.asList(roles.split(",")).contains("ADMIN"), "user.selfDisable",
                    "自分自身の管理権限を削除・停止することはできません。");
        }
        user.setDisplayName(Checks.text(displayName, "表示名", 100));
        user.setRoles(roles);
        user.setActive(active);
        audit(actor, "USER_UPDATE", user, "roles=" + roles + " active=" + active);
        dao.flush();
        return new UserSummary(user);
    }

    public UserSummary unlock(Long id, int expectedVersion, Actor actor) {
        require(actor, "ADMIN");
        AppUser user = dao.lock(AppUser.class, id);
        Checks.version(user.getVersion(), expectedVersion);
        Checks.state(user.isActive(), "user.disabled", "停止中の利用者は先に利用再開してください。");
        Checks.state(user.getFailedAttempts() > 0 || user.getLockedUntil() != null, "user.notLocked",
                "この利用者に解除対象の認証失敗・ロックはありません。");
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        audit(actor, "USER_UNLOCK", user, "manual unlock");
        dao.flush();
        return new UserSummary(user);
    }

    public UserSummary resetPassword(Long id, int expectedVersion, char[] password, Actor actor) {
        require(actor, "ADMIN");
        try {
            Checks.state(!actor.getUserId().equals(id), "user.ownPassword", "自分のパスワードはパスワード変更画面で変更してください。");
            Passwords.validateNew(password);
            AppUser user = dao.lock(AppUser.class, id);
            Checks.version(user.getVersion(), expectedVersion);
            user.setPasswordHash(Passwords.hash(password));
            user.setPasswordChangedAt(new Date());
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
            audit(actor, "USER_PASSWORD_RESET", user, "administrator reset");
            dao.flush();
            return new UserSummary(user);
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    private String roles(String csv) {
        Checks.text(csv, "権限", 200);
        Set<String> chosen = new LinkedHashSet<String>();
        for (String candidate : csv.split(",", -1)) {
            String role = candidate.trim();
            Checks.state(ALLOWED_ROLES.contains(role), "user.role", "指定した権限区分が不正です。");
            chosen.add(role);
        }
        StringBuilder result = new StringBuilder();
        for (String role : ALLOWED_ROLES) {
            if (chosen.contains(role)) {
                if (result.length() > 0) {
                    result.append(',');
                }
                result.append(role);
            }
        }
        return result.toString();
    }
}
