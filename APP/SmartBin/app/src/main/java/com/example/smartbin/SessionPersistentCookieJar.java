package com.example.smartbin;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class SessionPersistentCookieJar implements CookieJar {
    private static final String COOKIE_PREFS = "CookiePrefsFile";
    private final SharedPreferences sharedPreferences;

    public SessionPersistentCookieJar() {
        sharedPreferences = SmartBinApplication.getContext()
                .getSharedPreferences(COOKIE_PREFS, Context.MODE_PRIVATE);
    }

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Set<String> cookieSet = new HashSet<>();

        for (Cookie cookie : cookies) {
            // Convert cookie to a storable string format
            String cookieString = cookie.name() + "=" + cookie.value() +
                    "; Domain=" + cookie.domain() +
                    "; Path=" + cookie.path();
            cookieSet.add(cookieString);
        }

        editor.putStringSet("cookies", cookieSet);
        editor.apply();
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        Set<String> cookieSet = sharedPreferences.getStringSet("cookies", new HashSet<>());
        List<Cookie> cookies = new ArrayList<>();

        for (String cookieString : cookieSet) {
            Cookie cookie = parseCookie(cookieString, url);
            if (cookie != null) {
                cookies.add(cookie);
            }
        }

        return cookies;
    }

    private Cookie parseCookie(String cookieString, HttpUrl url) {
        try {
            // Basic parsing of the cookie string
            String[] parts = cookieString.split("; ");
            String[] nameValue = parts[0].split("=");

            Cookie.Builder builder = new Cookie.Builder()
                    .name(nameValue[0])
                    .value(nameValue[1])
                    .domain(url.host())
                    .path("/");

            return builder.build();
        } catch (Exception e) {
            return null;
        }
    }
}
