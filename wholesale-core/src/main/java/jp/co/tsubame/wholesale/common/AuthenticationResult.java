package jp.co.tsubame.wholesale.common;

public final class AuthenticationResult {
    private final Actor actor;
    private final String message;

    public AuthenticationResult(Actor actor, String message) {
        this.actor = actor;
        this.message = message;
    }

    public boolean isAuthenticated() {
        return actor != null;
    }

    public Actor getActor() {
        return actor;
    }

    public String getMessage() {
        return message;
    }
}
