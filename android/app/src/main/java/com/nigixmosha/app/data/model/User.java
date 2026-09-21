package com.nigixmosha.app.data.model;

public final class User {
    public String id;
    public String username;
    public String displayName;
    public String createdAt;

    public String name() {
        if (displayName != null && !displayName.trim().isEmpty()) return displayName.trim();
        return username == null ? "" : username;
    }

    public String firstName() {
        String name = name();
        int space = name.indexOf(' ');
        return space > 0 ? name.substring(0, space) : name;
    }
}
