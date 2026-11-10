package jp.co.tsubame.wholesale.common;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class Actor implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Long userId;
    private final String login;
    private final String displayName;
    private final Set<String> roles;

    public Actor(Long userId, String login, String displayName, String roleList) {
        if (userId == null || login == null || roleList == null) {
            throw new IllegalArgumentException("An authenticated actor is required");
        }
        this.userId = userId;
        this.login = login;
        this.displayName = displayName;
        Set<String> parsed = new LinkedHashSet<String>();
        for (String role : Arrays.asList(roleList.split(","))) {
            if (role.trim().length() != 0) {
                parsed.add(role.trim());
            }
        }
        roles = Collections.unmodifiableSet(parsed);
    }

    public void require(String... allowed) {
        if (hasRole("ADMIN")) {
            return;
        }
        for (String role : allowed) {
            if (roles.contains(role)) {
                return;
            }
        }
        throw new BusinessException("permission.denied", "この処理を実行する権限がありません。");
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public Long getUserId() {
        return userId;
    }

    public String getLogin() {
        return login;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<String> getRoles() {
        return roles;
    }
}
