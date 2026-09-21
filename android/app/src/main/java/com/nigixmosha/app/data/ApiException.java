package com.nigixmosha.app.data;

public final class ApiException extends Exception {
    public enum Kind { NETWORK, TIMEOUT, HTTP, PARSE }

    private final Kind kind;
    private final int status;
    private final String code;

    public ApiException(Kind kind, int status, String code, String message) {
        super(message);
        this.kind = kind;
        this.status = status;
        this.code = code;
    }

    public Kind kind() {
        return kind;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public boolean isUnauthorized() {
        return status == 401 && !"invalid_credentials".equals(code);
    }

    public boolean isOffline() {
        return kind == Kind.NETWORK || kind == Kind.TIMEOUT;
    }
}
