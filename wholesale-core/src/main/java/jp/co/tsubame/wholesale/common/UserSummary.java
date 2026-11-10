package jp.co.tsubame.wholesale.common;

import java.util.Date;
import jp.co.tsubame.wholesale.entity.AppUser;

public final class UserSummary {
    private final Long id;
    private final int version;
    private final String login;
    private final String displayName;
    private final String roles;
    private final boolean active;
    private final int failedAttempts;
    private final Date lockedUntil;
    private final Date lastLoginAt;
    private final Date passwordChangedAt;

    public UserSummary(AppUser user) {
        id = user.getId();
        version = user.getVersion();
        login = user.getLogin();
        displayName = user.getDisplayName();
        roles = user.getRoles();
        active = user.isActive();
        failedAttempts = user.getFailedAttempts();
        lockedUntil = user.getLockedUntil();
        lastLoginAt = user.getLastLoginAt();
        passwordChangedAt = user.getPasswordChangedAt();
    }

    public Long getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public String getLogin() {
        return login;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRoles() {
        return roles;
    }

    public boolean isActive() {
        return active;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Date getLockedUntil() {
        return lockedUntil;
    }

    public Date getLastLoginAt() {
        return lastLoginAt;
    }

    public Date getPasswordChangedAt() {
        return passwordChangedAt;
    }
}
