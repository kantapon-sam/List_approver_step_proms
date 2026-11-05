package com.java.myapp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class LoginCredentialsTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        LoginCredentials login = parse("\uFEFFUser, demo-user \r\nPassword,  test,comma password  \r\nSearch,\r\nDescription,\r\n");
        check(login.available(), "credentials available");
        check(login.username.equals("demo-user"), "trim username");
        check(login.password.equals("  test,comma password  "), "preserve password commas and spaces");
        check(!parse("# optional\nUser,\nPassword,\n").available(), "blank template permits manual login");
        check(parse("Username,demo\nPass,test\n").available(), "accepted aliases");
        rejects("User,demo\nPassword,\n");
        rejects("User,\nPassword,test\n");
        rejects("User,demo\nUser,duplicate\nPassword,test\n");
        rejects("User,demo\nPassword,test\nPassword,duplicate\n");
        String secretMarker = "FAKE_SECRET_MUST_NOT_BE_LOGGED";
        try { parse(secretMarker); throw new AssertionError("malformed config accepted"); }
        catch (IOException ex) { check(!ex.getMessage().contains(secretMarker), "parse error hides input content"); }
        Path directory = Files.createTempDirectory("proms-login-test-");
        Path file = directory.resolve("Login.txt");
        try {
            check(!LoginCredentials.readOptional(file).available(), "missing file permits manual login");
            Files.write(file, "User,ทดสอบ\nPassword,dummy\n".getBytes(StandardCharsets.UTF_8));
            check(LoginCredentials.readOptional(file).username.equals("ทดสอบ"), "UTF-8 credential file");
        } finally { Files.deleteIfExists(file); Files.delete(directory); }
        System.out.println("PASS: " + checks + " login configuration checks; synthetic credentials only.");
    }
    private static LoginCredentials parse(String text) throws IOException { return LoginCredentials.parse(new StringReader(text)); }
    private static void rejects(String text) throws Exception {
        try { parse(text); } catch (IOException expected) { checks++; return; }
        throw new AssertionError("Invalid login config accepted");
    }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
