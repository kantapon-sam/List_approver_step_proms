package com.java.myapp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;

/** Only navigation, search, disclosure and detail tabs are clickable. */
final class PromsBrowser implements Portal {
    private final WebDriver driver;
    private final Settings settings;
    private final BooleanSupplier cancelled;
    private final String script;

    PromsBrowser(WebDriver driver, Settings settings, BooleanSupplier cancelled) throws IOException {
        this.driver = driver; this.settings = settings; this.cancelled = cancelled;
        try (InputStream in = getClass().getResourceAsStream("resources/dom.js")) {
            if (in == null) throw new IOException("DOM adapter resource is missing.");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192]; int n;
            while ((n = in.read(data)) != -1) buffer.write(data, 0, n);
            script = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    void login(LoginCredentials credentials) {
        driver.get("https://proms.truecorp.co.th/proms/login/");
        prefillLogin(credentials);
        new WebDriverWait(driver, settings.loginSeconds).until(d -> {
            checkCancelled();
            String url = d.getCurrentUrl();
            return url.startsWith("https://proms.truecorp.co.th/proms/") && !url.contains("/login")
                && d.findElements(By.id("btn_signin")).isEmpty();
        });
    }

    void prefillLogin(LoginCredentials credentials) {
        if (!credentials.available()) return;
        if (!driver.getCurrentUrl().startsWith("https://proms.truecorp.co.th/proms/login/"))
            throw new IllegalStateException("Expected the PROMs login page before filling credentials.");
        WebElement username = waitFor().until(ExpectedConditions.visibilityOfElementLocated(By.id("username")));
        WebElement password = waitFor().until(ExpectedConditions.visibilityOfElementLocated(By.id("password")));
        checkCancelled();
        username.clear(); username.sendKeys(credentials.username);
        password.clear(); password.sendKeys(credentials.password);
        // Match the reference application: the user completes CAPTCHA/OTP and clicks Sign in.
    }

    public void search(String value) {
        checkCancelled();
        driver.get(settings.listUrl);
        WebElement input = waitFor().until(ExpectedConditions.elementToBeClickable(By.cssSelector(settings.searchCss)));
        settle();
        input.clear(); input.sendKeys(value); input.sendKeys(Keys.ENTER);
        settle();
        if (!value.equals(driver.findElement(By.cssSelector(settings.searchCss)).getAttribute("value")))
            throw new IllegalStateException("Search input did not retain the requested ID.");
        requireTable("requests");
    }

    public List<Map<String,String>> allRows(String kind) {
        List<Map<String,String>> result = new ArrayList<>();
        Set<String> pages = new HashSet<>();
        for (int page = 0; page < settings.maxPages; page++) {
            List<Map<String,String>> rows = snapshot(kind);
            if (!pages.add(rows.toString())) throw new IllegalStateException("Repeated " + kind + " page; export would be incomplete.");
            result.addAll(rows);
            WebElement next = (WebElement) dom("next", kind, null);
            if (next == null) return result;
            click(next); settle();
        }
        throw new IllegalStateException("Reached max.pages for " + kind + "; export would be incomplete.");
    }

    public void openRequest(String ref) {
        search(ref);
        openPagedLink("requests", ref);
        waitFor().until(d -> dom("section", null, null) != null || Boolean.TRUE.equals(dom("exists", "steps", null)));
        if (!Boolean.TRUE.equals(dom("exists", "steps", null))) {
            click((WebElement) dom("section", null, null)); settle();
        }
        requireTable("steps");
    }

    public List<Map<String,String>> approvals(String step) {
        openPagedLink("steps", step);
        WebElement tab = waitFor().until(d -> (WebElement) dom("approvalTab", null, null));
        click(tab); settle();
        requireTable("approvals");
        return allRows("approvals");
    }

    private void openPagedLink(String kind, String value) {
        Set<String> pages = new HashSet<>();
        for (int page = 0; page < settings.maxPages; page++) {
            List<Map<String,String>> rows = snapshot(kind);
            if (!pages.add(rows.toString())) throw new IllegalStateException("Repeated page while opening " + value);
            WebElement link = (WebElement) dom("link", kind, value);
            if (link != null) { click(link); settle(); return; }
            WebElement next = (WebElement) dom("next", kind, null);
            if (next == null) throw new IllegalStateException("Detail link not found: " + value);
            click(next); settle();
        }
        throw new IllegalStateException("Reached page limit while opening " + value);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,String>> snapshot(String kind) {
        requireTable(kind);
        return (List<Map<String,String>>) dom("snapshot", kind, null);
    }
    private void requireTable(String kind) {
        waitFor().withMessage("Could not identify the " + kind + " table by its column headings.")
            .until(d -> Boolean.TRUE.equals(dom("exists", kind, null)));
    }
    private Object dom(String operation, String kind, String value) {
        checkCancelled();
        return ((JavascriptExecutor) driver).executeScript(script, operation, kind, value);
    }
    private void click(WebElement element) {
        checkCancelled();
        if (element == null) throw new IllegalStateException("Navigation control is missing.");
        String label = element.getText().trim();
        String action = String.valueOf(element.getAttribute("ng-click"));
        String href = String.valueOf(element.getAttribute("href"));
        if (label.matches("(?i)^(approve|reject|submit|save|delete|confirm|ok)$")
                || action.matches("(?is).*(approvalAction|paOkModal|delete|save|submit).*")
                || href.toLowerCase(Locale.ROOT).startsWith("javascript:")
                    && !href.matches("(?i)javascript:\\s*(void\\(0\\)|;)\\s*;?"))
            throw new IllegalStateException("Blocked a non-navigation control.");
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
        waitFor().until(ExpectedConditions.elementToBeClickable(element)).click();
    }
    private WebDriverWait waitFor() { return new WebDriverWait(driver, settings.timeoutSeconds); }
    private void settle() {
        final String[] previous = {null};
        final long[] since = {System.nanoTime()};
        waitFor().withMessage("Page did not finish loading within timeout.seconds.").until(d -> {
            checkCancelled();
            if (d.getCurrentUrl().contains("/login")) throw new IllegalStateException("PROMs session expired. Sign in and run again.");
            boolean idle = Boolean.TRUE.equals(dom("idle", null, null));
            String current = String.valueOf(dom("signature", null, null));
            if (!idle || !current.equals(previous[0])) { previous[0] = current; since[0] = System.nanoTime(); return false; }
            return (System.nanoTime() - since[0]) / 1000000 >= settings.quietMillis;
        });
    }
    private void checkCancelled() {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new Cancelled();
    }
    static final class Cancelled extends RuntimeException { }
}
