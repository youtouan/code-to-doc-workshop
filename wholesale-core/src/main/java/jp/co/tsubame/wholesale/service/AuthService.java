package jp.co.tsubame.wholesale.service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.AuthenticationResult;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Passwords;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.AppUser;

public class AuthService extends BaseService {
    private static final String FAILURE = "利用者IDまたはパスワードが不正、もしくは利用停止中です。";
    private final String dummyHash = Passwords.hash("Not-an-account-82731".toCharArray());

    public AuthenticationResult authenticate(String login, char[] password) {
        char[] supplied = password == null ? new char[0] : password;
        try {
            String normalized = login == null ? "" : login.trim().toLowerCase(Locale.ROOT);
            if (normalized.length() > 50 || supplied.length > 128) {
                return new AuthenticationResult(null, FAILURE);
            }
            List<AppUser> found = dao.list("from AppUser where login = :login",
                    WholesaleDao.params("login", normalized));
            if (found.isEmpty()) {
                Passwords.matches(supplied, dummyHash);
                return new AuthenticationResult(null, FAILURE);
            }
            AppUser user = dao.lock(AppUser.class, found.get(0).getId());
            Date now = new Date();
            boolean valid = Passwords.matches(supplied, user.getPasswordHash());
            if (!user.isActive() || (user.getLockedUntil() != null && user.getLockedUntil().after(now))) {
                return new AuthenticationResult(null, FAILURE);
            }
            if (!valid) {
                if (user.getLockedUntil() != null) {
                    user.setFailedAttempts(0);
                    user.setLockedUntil(null);
                }
                user.setFailedAttempts(user.getFailedAttempts() + 1);
                if (user.getFailedAttempts() >= 5) {
                    user.setLockedUntil(new Date(now.getTime() + 5L * 60L * 1000L));
                }
                return new AuthenticationResult(null, FAILURE);
            }
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
            user.setLastLoginAt(now);
            Actor actor = actor(user);
            audit(actor, "LOGIN", user, "login");
            return new AuthenticationResult(actor, "");
        } finally {
            Arrays.fill(supplied, '\0');
        }
    }

    public Actor getCurrentActor(Long userId) {
        AppUser user = dao.get(AppUser.class, userId);
        Checks.state(user.isActive(), "authentication.disabled", "この利用者は利用停止中です。");
        return actor(user);
    }

    public void changePassword(char[] currentPassword, char[] newPassword, Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "BILLING", "MANAGER", "BATCH");
        try {
            AppUser user = dao.lock(AppUser.class, actor.getUserId());
            Checks.state(Passwords.matches(currentPassword, user.getPasswordHash()),
                    "password.current", "現在のパスワードが一致しません。");
            Passwords.validateNew(newPassword);
            Checks.state(!Passwords.matches(newPassword, user.getPasswordHash()),
                    "password.reuse", "現在とは異なるパスワードを指定してください。");
            user.setPasswordHash(Passwords.hash(newPassword));
            user.setPasswordChangedAt(new Date());
            audit(actor, "PASSWORD_CHANGE", user, "own password");
        } finally {
            if (currentPassword != null) {
                Arrays.fill(currentPassword, '\0');
            }
            if (newPassword != null) {
                Arrays.fill(newPassword, '\0');
            }
        }
    }

    private Actor actor(AppUser user) {
        return new Actor(user.getId(), user.getLogin(), user.getDisplayName(), user.getRoles());
    }
}
