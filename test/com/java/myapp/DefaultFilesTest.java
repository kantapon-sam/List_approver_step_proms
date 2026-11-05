package com.java.myapp;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class DefaultFilesTest {
    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory("proms-defaults-test-");
        try {
            DefaultFiles.initialize(home);
            require(!LoginCredentials.readOptional(home.resolve("Login.txt")).available(), "blank login");
            require(read(home.resolve("input.csv")).equals("Ref No,Project ID\n"), "header only, no Steps column");
            require(Files.size(home.resolve("proms.properties")) == 0, "blank settings");
            require(Settings.read(home.resolve("proms.properties")).loginSeconds == 300, "blank settings use defaults");
            Map<String,byte[]> privateFiles = new LinkedHashMap<>();
            for (String name : new String[]{"Login.txt","input.csv","proms.properties","input.example.csv"}) {
                byte[] bytes = ("user content in " + name).getBytes(StandardCharsets.UTF_8);
                privateFiles.put(name, bytes); Files.write(home.resolve(name), bytes);
            }
            // A known, unedited v1.0.0 template may be retired; edited files must survive.
            Files.write(home.resolve("Login.example.txt"), "User,\nPassword,\n".getBytes(StandardCharsets.UTF_8));
            DefaultFiles.initialize(home);
            require(!Files.exists(home.resolve("Login.example.txt")), "unedited obsolete template removed");
            for (Map.Entry<String,byte[]> entry : privateFiles.entrySet())
                require(Arrays.equals(entry.getValue(), Files.readAllBytes(home.resolve(entry.getKey()))), "user data preserved: " + entry.getKey());
            System.out.println("PASS: blank real-name defaults, no Steps column, default settings, migration and user-data preservation.");
        } finally {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(home)) { for (Path file : files) Files.delete(file); }
            Files.delete(home);
        }
    }
    private static String read(Path file) throws Exception { return new String(Files.readAllBytes(file), StandardCharsets.UTF_8).replace("\r\n", "\n"); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
