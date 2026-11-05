package com.java.myapp;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

final class Settings {
    final String listUrl;
    final int timeoutSeconds, loginSeconds, quietMillis, maxPages;
    final String searchCss;

    Settings(Properties p) throws IOException {
        listUrl = p.getProperty("list.url", "https://proms.truecorp.co.th/proms/pendingapprove").trim();
        try {
            URI uri = new URI(listUrl);
            if (!"https".equals(uri.getScheme()) || !"proms.truecorp.co.th".equals(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1 || !uri.getPath().startsWith("/proms/"))
                throw new IllegalArgumentException();
        } catch (Exception ex) { throw new IOException("list.url must be an HTTPS page under proms.truecorp.co.th/proms/."); }
        timeoutSeconds = number(p, "timeout.seconds", 45, 5, 300);
        loginSeconds = number(p, "login.seconds", 300, 30, 1800);
        quietMillis = number(p, "quiet.millis", 1800, 500, 10000);
        if (quietMillis >= timeoutSeconds * 1000)
            throw new IOException("quiet.millis must be less than timeout.seconds * 1000.");
        maxPages = number(p, "max.pages", 2000, 1, 20000);
        searchCss = p.getProperty("search.css", "#txtSearch").trim();
        if (searchCss.isEmpty()) throw new IOException("search.css cannot be empty.");
    }
    static Settings read(Path file) throws IOException {
        Properties p = new Properties();
        if (Files.exists(file)) try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(r); }
        return new Settings(p);
    }
    private static int number(Properties p, String key, int fallback, int min, int max) throws IOException {
        try {
            int value = Integer.parseInt(p.getProperty(key, "" + fallback));
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) { throw new IOException("Invalid setting: " + key); }
    }
}
