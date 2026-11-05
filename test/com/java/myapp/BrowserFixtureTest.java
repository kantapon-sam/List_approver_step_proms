package com.java.myapp;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.chrome.*;

/** Real Selenium/DOM integration, restricted to an ephemeral loopback server. No PROMs traffic. */
public final class BrowserFixtureTest {
    public static void main(String[] args) throws Exception {
        byte[] fixture = Files.readAllBytes(Paths.get("test/fixtures/proms.html"));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        byte[] loginFixture = ("<!doctype html><html><body><input id='username' value='previous-user'>"
            + "<input id='password' type='password' value='previous-password'>"
            + "<button id='btn_signin' onclick='window.loginClicks++'>Sign in</button>"
            + "<script>window.loginClicks=0;</script></body></html>").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        server.createContext("/login/", exchange -> {
            exchange.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");
            exchange.sendResponseHeaders(200,loginFixture.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(loginFixture); }
        });
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");
            exchange.getResponseHeaders().set("Content-Security-Policy","default-src 'self' 'unsafe-inline'");
            exchange.sendResponseHeaders(200,fixture.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(fixture); }
        });
        server.start();
        ChromeOptions options = new ChromeOptions(); options.setBinary(new Download_ChromeDriver().findChromeBinary());
        options.addArguments("--headless=new","--disable-gpu","--no-first-run","--disable-background-networking");
        ChromeDriver driver = null;
        try {
            driver = new FixtureDriver(options,"http://127.0.0.1:"+server.getAddress().getPort()+"/");
            Properties props = new Properties(); props.setProperty("quiet.millis","500"); props.setProperty("timeout.seconds","8");
            PromsBrowser browser = new PromsBrowser(driver,new Settings(props),() -> false);
            driver.get("https://proms.truecorp.co.th/proms/login/");
            LoginCredentials dummy = LoginCredentials.parse(new StringReader("User,fixture-user\nPassword,fixture,pass\n"));
            browser.prefillLogin(dummy);
            require(driver.findElement(org.openqa.selenium.By.id("username")).getAttribute("value").equals("fixture-user"),"username prefilled");
            require(driver.findElement(org.openqa.selenium.By.id("password")).getAttribute("value").equals("fixture,pass"),"password prefilled with comma preserved");
            require(((Number)driver.executeScript("return window.loginClicks")).intValue()==0,"sign-in waits for the user");
            browser.prefillLogin(LoginCredentials.parse(new StringReader("User,\nPassword,\n")));
            require(driver.findElement(org.openqa.selenium.By.id("username")).getAttribute("value").equals("fixture-user"),"manual fallback leaves fields untouched");
            browser.search("240258");
            try { browser.prefillLogin(dummy); throw new AssertionError("credentials filled outside login page"); }
            catch (IllegalStateException expected) { }
            require(browser.allRows("requests").size()==3,"request pagination");
            StringWriter report = new StringWriter(); Csv.write(report,Exporter.HEADER);
            Exporter exporter = new Exporter(browser,report,"LOCAL FIXTURE ONLY",System.out::println);
            exporter.runAll();
            List<List<String>> rows = Csv.parse(new StringReader(report.toString()));
            require(exporter.rows==12,"all three requests and both steps exported without CSV input");
            require(exporter.problems==0,"automatic export has no issues");
            require(rows.get(1).get(8).equals("NT_IE_TK07"),"first step");
            require(rows.get(3).get(8).equals("NT_IE_TK08"),"second step from next page");
            require(rows.get(3).get(11).equals("NT_IE_TK08_TEST_1"),"no stale modal data");
            require(rows.get(2).get(13).isEmpty(),"pending date blank");
            require(rows.size()==13,"header and all twelve approval rows");
            require(rows.stream().skip(1).map(row -> row.get(3)).distinct().count()==3,"every request page included");
            require(((Number)((JavascriptExecutor)driver).executeScript("return window.writeActions")).intValue()==0,"no approve actions");
            // A missing Approval Line column must fail visibly rather than export shifted values.
            browser.openRequest("26070892");
            ((JavascriptExecutor)driver).executeScript("var old=approvals; approvals=function(s,p){old(s,p);document.querySelector('#approvals th:last-child').innerText='Unexpected Column'};");
            try { browser.approvals("NT_IE_TK07"); throw new AssertionError("changed columns accepted"); }
            catch (org.openqa.selenium.TimeoutException expected) { }
            System.out.println("PASS: local browser fixture: login prefill/manual submit, request/step/approval pagination, async loads, exact IDs, blank dates, modal isolation, changed-column rejection.");
        } finally { if (driver!=null) driver.quit(); server.stop(0); }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static final class FixtureDriver extends ChromeDriver {
        private final String fixtureUrl;
        FixtureDriver(ChromeOptions options,String fixtureUrl) { super(options); this.fixtureUrl=fixtureUrl; }
        @Override public void get(String url) {
            if (url.equals("https://proms.truecorp.co.th/proms/login/")) { super.get(fixtureUrl+"login/"); return; }
            if (!url.equals("https://proms.truecorp.co.th/proms/pendingapprove")) throw new AssertionError("Unexpected navigation in offline test: "+url);
            super.get(fixtureUrl);
        }
        @Override public String getCurrentUrl() {
            String actual = super.getCurrentUrl();
            return actual.equals(fixtureUrl+"login/") ? "https://proms.truecorp.co.th/proms/login/" : actual;
        }
    }
}
