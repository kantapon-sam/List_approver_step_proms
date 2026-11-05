package com.java.myapp;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;

/** Synthetic authenticated portal on loopback. No real account or PROMs traffic. */
public final class BrowserSessionFixtureTest {
    public static void main(String[] args) throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        String guard="<script>if(localStorage.getItem('fixture-local')!=='local-value'||sessionStorage.getItem('fixture-session')!=='session-value')location.replace('/login/');</script>";
        byte[] portal=(guard+new String(Files.readAllBytes(Paths.get("test/fixtures/proms.html")),StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
        byte[] login="<html><body>Fixture login</body></html>".getBytes(StandardCharsets.UTF_8);
        server.createContext("/",exchange -> {
            boolean isLogin=exchange.getRequestURI().getPath().equals("/login/");
            String cookie=exchange.getRequestHeaders().getFirst("Cookie");
            String auth=cookie==null?"":Arrays.stream(cookie.split(";")).map(String::trim).filter(c -> c.startsWith("fixture-auth=")).findFirst().orElse("");
            if(!isLogin && (!auth.equals("fixture-auth=cookie-value") || !"PROMs-Fixture-Login".equals(exchange.getRequestHeaders().getFirst("User-Agent")))) {
                exchange.getResponseHeaders().set("Location","/login/"); exchange.sendResponseHeaders(302,-1); exchange.close(); return;
            }
            if(isLogin) exchange.getResponseHeaders().set("Set-Cookie","fixture-auth=logged-out; Path=/login/; HttpOnly");
            byte[] body=isLogin?login:portal;
            exchange.getResponseHeaders().set("Content-Type","text/html; charset=utf-8"); exchange.sendResponseHeaders(200,body.length);
            try(OutputStream out=exchange.getResponseBody()) { out.write(body); }
        });
        String base="http://127.0.0.1:"+server.getAddress().getPort()+"/";
        ChromeOptions options=new ChromeOptions(); options.setBinary(new Download_ChromeDriver().findChromeBinary());
        options.addArguments("--headless=new","--no-first-run","--disable-background-networking");
        Properties props=new Properties(); props.setProperty("quiet.millis","500"); props.setProperty("timeout.seconds","5"); Settings settings=new Settings(props);
        FixtureDriver source=null; FixtureDriver[] target={null};
        server.start();
        try {
            ChromeOptions loginOptions=new ChromeOptions(); loginOptions.setBinary(new Download_ChromeDriver().findChromeBinary());
            loginOptions.addArguments("--headless=new","--no-first-run","--user-agent=PROMs-Fixture-Login");
            source=new FixtureDriver(loginOptions,base,false); source.get(BrowserSession.LOGIN_URL);
            try { BrowserSession.inBackground(source,() -> { throw new AssertionError("must not create browser before login"); },settings,() -> false); throw new AssertionError("signed-out source accepted"); } catch(IOException expected) { }
            source.manage().deleteAllCookies();
            source.manage().addCookie(new Cookie.Builder("fixture-auth","cookie-value").path("/").isHttpOnly(true).build());
            source.executeScript("localStorage.setItem('fixture-local','local-value');sessionStorage.setItem('fixture-session','session-value');"); source.get(settings.listUrl);
            try { BrowserSession.inBackground(source,() -> target[0]=new FixtureDriver(options,base,true),settings,() -> false); throw new AssertionError("signed-out target accepted"); }
            catch(IOException expected) { require(expected.getCause()==null && !expected.getMessage().contains("cookie-value"),"failure does not expose auth details"); }
            require(target[0].getSessionId()==null && source.getSessionId()!=null,"failure closes background and retains sign-in browser");
            AtomicBoolean cancel=new AtomicBoolean();
            try { BrowserSession.inBackground(source,() -> { target[0]=new FixtureDriver(options,base,false); cancel.set(true); return target[0]; },settings,cancel::get); throw new AssertionError("cancellation ignored"); }
            catch(PromsBrowser.Cancelled expected) { }
            require(target[0].getSessionId()==null && source.getSessionId()!=null,"cancellation closes new browser and preserves source");
            WebDriver background=BrowserSession.inBackground(source,() -> target[0]=new FixtureDriver(options,base,false),settings,() -> false);
            require(source.getSessionId()==null && target[0].getSessionId()!=null,"successful handoff closes only sign-in browser");
            require(background.manage().getCookieNamed("fixture-auth").isHttpOnly(),"HttpOnly session cookie transferred");
            require(background.manage().getCookies().stream().noneMatch(c -> c.getValue().equals("logged-out")),"conflicting logged-out session cookie removed");
            require("PROMs-Fixture-Login".equals(target[0].executeScript("return navigator.userAgent")),"login browser identity retained");
            require("local-value".equals(target[0].executeScript("return localStorage.getItem('fixture-local')")),"local storage transferred");
            require("session-value".equals(target[0].executeScript("return sessionStorage.getItem('fixture-session')")),"session storage transferred");
            PromsBrowser browser=new PromsBrowser(background,settings,() -> false); browser.search("26070892");
            require(browser.currentRows("requests").size()==1,"authenticated extraction continues in background");
            System.out.println("PASS: authenticated browser handoff, conflicting cookie removal, browser identity, HttpOnly cookies and both storage types, closed login browser, failure cleanup and cancellation.");
        } finally {
            if(target[0]!=null) target[0].quit(); if(source!=null) source.quit(); server.stop(0);
        }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static final class FixtureDriver extends ChromeDriver {
        final String base; final boolean forceSignedOut;
        FixtureDriver(ChromeOptions options,String base,boolean forceSignedOut) { super(options); this.base=base; this.forceSignedOut=forceSignedOut; }
        @Override public void get(String url) {
            if(url.equals(BrowserSession.LOGIN_URL)) super.get(base+"login/");
            else if(url.equals("https://proms.truecorp.co.th/proms/pendingapprove")) super.get(base+(forceSignedOut?"login/":"login/list/"));
            else throw new AssertionError("Unexpected offline navigation");
        }
        @Override public String getCurrentUrl() {
            String actual=super.getCurrentUrl();
            if(actual.equals(base+"login/")) return BrowserSession.LOGIN_URL;
            if(actual.equals(base+"login/list/")) return "https://proms.truecorp.co.th/proms/pendingapprove";
            return actual;
        }
    }
}
