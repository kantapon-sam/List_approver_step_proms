package com.java.myapp;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.remote.*;

/** Uses two isolated automation sessions; authentication is transferred in memory only. */
final class BrowserSession {
    static final String LOGIN_URL = "https://proms.truecorp.co.th/proms/login/";

    static WebDriver create(File chrome, Settings settings, boolean background) {
        ChromeOptions options = new ChromeOptions(); options.setBinary(chrome);
        if (background) options.addArguments("--headless=new", "--window-size=1920,1080");
        WebDriver driver = new ChromeDriver(options);
        try {
            driver.manage().timeouts().pageLoadTimeout(settings.timeoutSeconds, TimeUnit.SECONDS);
            driver.manage().timeouts().setScriptTimeout(settings.timeoutSeconds, TimeUnit.SECONDS);
            if (!background) driver.manage().window().maximize();
            return driver;
        } catch (RuntimeException failure) {
            try { driver.quit(); } catch (RuntimeException ignored) { }
            throw failure;
        }
    }

    static WebDriver inBackground(WebDriver signedIn, Supplier<WebDriver> createBackground,
            Settings settings, BooleanSupplier stop) throws IOException {
        check(stop);
        String url = signedIn.getCurrentUrl();
        if (!url.startsWith("https://proms.truecorp.co.th/proms/") || url.contains("/login"))
            throw new IOException("Sign in to PROMs before starting background extraction.");
        Set<Cookie> cookies = new HashSet<>(signedIn.manage().getCookies());
        Object storage = ((JavascriptExecutor)signedIn).executeScript(
            "return [Object.keys(localStorage).map(function(k){return [k,localStorage.getItem(k)];})," +
            "Object.keys(sessionStorage).map(function(k){return [k,sessionStorage.getItem(k)];})];");
        WebDriver background = null;
        boolean success = false;
        String stage = "starting Chrome";
        try {
            background = createBackground.get();
            check(stop);
            stage = "matching the signed-in browser";
            matchUserAgent(signedIn,background);
            stage = "opening PROMs";
            background.get(LOGIN_URL);
            if (!background.getCurrentUrl().startsWith("https://proms.truecorp.co.th/proms/"))
                throw new IOException("The background browser is not on PROMs.");
            stage = "transferring cookies";
            // The login page can create a new session cookie with a more specific path.
            // Remove that session before restoring the user's authenticated cookies.
            background.manage().deleteAllCookies();
            for (Cookie cookie : cookies) { check(stop); background.manage().addCookie(cookie); }
            stage = "transferring browser storage";
            ((JavascriptExecutor)background).executeScript(
                "var s=arguments[0];localStorage.clear();sessionStorage.clear();" +
                "s[0].forEach(function(p){localStorage.setItem(p[0],p[1]);});" +
                "s[1].forEach(function(p){sessionStorage.setItem(p[0],p[1]);});", storage);
            stage = "opening the request list";
            background.get(settings.listUrl);
            stage = "verifying sign-in";
            new PromsBrowser(background,settings,stop).verifySession();
            check(stop);
            signedIn.quit();
            success = true;
            return background;
        } catch (PromsBrowser.Cancelled cancelled) { throw cancelled; }
        catch (Exception failure) {
            // Driver errors can contain cookie values: never include their text in logs or dialogs.
            throw new IOException("Could not start the background session while " + stage
                + " (" + failure.getClass().getSimpleName() + "). Please sign in and try again.");
        } finally {
            cookies.clear(); storage = null;
            if (!success && background != null) try { background.quit(); } catch (RuntimeException ignored) { }
        }
    }
    private static void matchUserAgent(WebDriver source,WebDriver target) throws IOException {
        // PROMs may bind its session to the login browser's User-Agent. Headless Chrome changes it.
        String userAgent=(String)((JavascriptExecutor)source).executeScript("return navigator.userAgent;");
        RemoteWebDriver remote=(RemoteWebDriver)target;
        URL server=((HttpCommandExecutor)remote.getCommandExecutor()).getAddressOfRemoteServer();
        // ChromeDriver's CDP endpoint works with the Java 8 Selenium client.
        // https://chromium.googlesource.com/chromium/src/+/main/chrome/test/chromedriver/server/http_handler.cc
        URL endpoint=new URL(server,"/session/"+remote.getSessionId()+"/goog/cdp/execute");
        HttpURLConnection connection=(HttpURLConnection)endpoint.openConnection(java.net.Proxy.NO_PROXY);
        connection.setConnectTimeout(10000); connection.setReadTimeout(10000); connection.setRequestMethod("POST"); connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
        Map<String,Object> command=new HashMap<>(); command.put("cmd","Network.setUserAgentOverride"); command.put("params",Collections.singletonMap("userAgent",userAgent));
        try {
            try(OutputStream out=connection.getOutputStream()) { out.write(new com.google.gson.Gson().toJson(command).getBytes(StandardCharsets.UTF_8)); }
            if(connection.getResponseCode()!=200) throw new IOException("Could not preserve the signed-in browser identity.");
            try(InputStream response=connection.getInputStream()) { byte[] buffer=new byte[1024]; while(response.read(buffer)!=-1) { } }
            if(!userAgent.equals(((JavascriptExecutor)target).executeScript("return navigator.userAgent;")))
                throw new IOException("The background browser identity differs from sign-in.");
        } finally { connection.disconnect(); }
    }
    private static void check(BooleanSupplier stop) { if (stop.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new PromsBrowser.Cancelled(); }
}
