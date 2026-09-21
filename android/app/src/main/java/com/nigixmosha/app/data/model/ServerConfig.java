package com.nigixmosha.app.data.model;

import java.util.HashMap;
import java.util.Map;

public final class ServerConfig {
    public boolean brain = true;
    public Map<String, Boolean> serverKeys = new HashMap<>();
    public Storage storage = new Storage();

    public boolean hasServerKey(String provider) {
        Boolean v = serverKeys == null ? null : serverKeys.get(provider);
        return v != null && v;
    }

    public static final class Storage {
        public boolean db;
        public boolean media;
    }
}
