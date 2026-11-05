package com.java.myapp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;

/** Compatible with the reference program's User,value / Password,value format. */
final class LoginCredentials {
    final String username;
    final String password;

    private LoginCredentials(String username, String password) {
        this.username = username; this.password = password;
    }
    boolean available() { return !username.isEmpty(); }

    static LoginCredentials readOptional(Path file) throws IOException {
        if (!Files.exists(file)) return new LoginCredentials("", "");
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { return parse(reader); }
    }

    static LoginCredentials parse(Reader source) throws IOException {
        BufferedReader reader = new BufferedReader(source);
        String user = "", password = "", line;
        boolean userSeen = false, passwordSeen = false;
        int number = 0;
        while ((line = reader.readLine()) != null) {
            number++;
            if (number == 1 && line.startsWith("\uFEFF")) line = line.substring(1);
            if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
            String[] pair = line.split(",", 2);
            if (pair.length != 2) throw new IOException("Login.txt line " + number + ": expected Key,value.");
            String key = pair[0].trim().toLowerCase(Locale.ROOT);
            if (key.equals("user") || key.equals("username")) {
                if (userSeen) throw new IOException("Login.txt contains duplicate User entries.");
                user = pair[1].trim(); userSeen = true;
            } else if (key.equals("password") || key.equals("pass")) {
                if (passwordSeen) throw new IOException("Login.txt contains duplicate Password entries.");
                password = pair[1]; passwordSeen = true;
            }
            // Search and Description from the original application are intentionally ignored.
        }
        if (user.isEmpty() != password.trim().isEmpty())
            throw new IOException("Login.txt needs both User and Password, or leave both blank for manual sign-in.");
        return new LoginCredentials(user, password);
    }
}
