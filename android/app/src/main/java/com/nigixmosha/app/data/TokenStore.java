package com.nigixmosha.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.nigixmosha.app.data.model.User;

public final class TokenStore {
    private static final String PREFS = "nigix_session_store";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER = "user";

    private final SharedPreferences prefs;
    private final SecureBox box = new SecureBox("nigix_session_key");
    private final Gson gson = new Gson();
    private String cachedToken;
    private boolean tokenLoaded;

    TokenStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized String token() {
        if (tokenLoaded) return cachedToken;
        tokenLoaded = true;
        String stored = prefs.getString(KEY_TOKEN, null);
        if (stored == null) return cachedToken = null;
        try {
            cachedToken = box.open(stored);
        } catch (Exception e) {
            prefs.edit().remove(KEY_TOKEN).remove(KEY_USER).apply();
            cachedToken = null;
        }
        return cachedToken;
    }

    synchronized void saveToken(String token) {
        if (tokenLoaded && token.equals(cachedToken)) return;
        try {
            prefs.edit().putString(KEY_TOKEN, box.seal(token)).apply();
            cachedToken = token;
            tokenLoaded = true;
        } catch (Exception e) {
            clear();
        }
    }

    public synchronized User user() {
        String json = prefs.getString(KEY_USER, null);
        if (json == null) return null;
        try {
            return gson.fromJson(json, User.class);
        } catch (Exception e) {
            return null;
        }
    }

    synchronized void saveUser(User user) {
        prefs.edit().putString(KEY_USER, gson.toJson(user)).apply();
    }

    synchronized void clear() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USER).apply();
        cachedToken = null;
        tokenLoaded = true;
    }
}
