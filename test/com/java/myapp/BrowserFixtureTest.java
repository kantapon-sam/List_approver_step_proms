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
            browser.search("P-TEST-BASE");
            try { browser.prefillLogin(dummy); throw new AssertionError("credentials filled outside login page"); }
            catch (IllegalStateException expected) { }
            require(browser.allRows("requests").size()==3,"request pagination");
            driver.executeScript("localStorage.setItem('requestNextClicks','0'); localStorage.setItem('searchCount','0'); localStorage.setItem('openRequests','0');");
            StringWriter report = new StringWriter(); Csv.write(report,Exporter.HEADER);
            Exporter exporter = new Exporter(browser,report,"LOCAL FIXTURE ONLY",System.out::println);
            exporter.run(ExportTest.queries("Ref No,Project ID\n26070892,\n26070893,\n"));
            List<List<String>> rows = Csv.parse(new StringReader(report.toString()));
            require(exporter.rows==10,"both input references and every step, including grouped approvers, exported");
            require(exporter.problems==0,"automatic export has no issues");
            require(rows.get(1).get(2).equals("TK07") && rows.get(1).get(3).equals("NT_IE_TK07_TEST_1"),"first step and approver");
            require(rows.get(3).get(2).equals("TK08") && rows.get(3).get(3).equals("NT_IE_TK08_TEST_1"),"second step from next page without stale modal data");
            require(rows.get(2).get(5).isEmpty(),"pending date blank");
            require(rows.size()==11,"header and all ten approval rows");
            require(rows.get(4).get(3).equals("NT_IE_TK08_TEST_1B") && rows.get(4).get(4).equals("Pending"),"rowspan keeps approver and status aligned");
            require(rows.stream().allMatch(row -> row.size()==6),"exactly six columns in every row");
            require(rows.stream().skip(1).map(row -> row.get(0)).distinct().count()==2,"both input references included");
            require(rows.stream().skip(1).noneMatch(row -> row.get(0).equals("260708920")),"similar unrelated reference excluded");
            require("0".equals(driver.executeScript("return localStorage.getItem('requestNextClicks');")),"direct references never click request pagination");
            require("2".equals(driver.executeScript("return localStorage.getItem('searchCount');")),"exactly one search per input reference");
            require("2".equals(driver.executeScript("return localStorage.getItem('openRequests');")),"exactly one request opening per reference, not per step");
            require(((Number)((JavascriptExecutor)driver).executeScript("return window.writeActions")).intValue()==0,"no approve actions");
            browser.openRequest("26070892");
            final ChromeDriver streamingDriver=driver;
            final int[] deliveredPages={0};
            browser.readApprovals("NT_IE_TK08",page -> {
                deliveredPages[0]++;
                require(page.size()==(deliveredPages[0]==1?2:1),"approval data is delivered one page at a time");
                String shown=(String)streamingDriver.executeScript("return document.querySelector('#approvals .pagination .active').innerText;");
                require(shown.equals(Integer.toString(deliveredPages[0])),"page is delivered before clicking the next page");
            });
            require(deliveredPages[0]==2,"all streamed approval pages delivered");
            // A missing Approval Line column must fail visibly rather than export shifted values.
            browser.openRequest("26070892");
            ((JavascriptExecutor)driver).executeScript("var old=approvals; approvals=function(s,p){old(s,p);document.querySelector('#approvals th:last-child').innerText='Unexpected Column'};");
            try { browser.approvals("NT_IE_TK07"); throw new AssertionError("changed columns accepted"); }
            catch (org.openqa.selenium.TimeoutException expected) { }
            System.out.println("PASS: local browser fixture: one search/open per reference, two inputs, six output columns, grouped approvers with rowspan, filtered project pages, all step/approval pages, blank dates, modal isolation and changed-column rejection.");
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
