package com.java.myapp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Exercises version selection and recovery without network/browser access. */
public final class ChromeDriverUpdateTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        equal("156.0.8078.80", Download_ChromeDriver.parseVersion(
                "ChromeDriver 156.0.8078.80 (revision)", "ChromeDriver"), "driver version");
        check(Download_ChromeDriver.sameBuild("156.0.8078.74", "156.0.8078.80"), "patch difference is compatible");
        check(!Download_ChromeDriver.sameBuild("156.0.8078.74", "155.0.8059.39"), "different milestone");
        check(!Download_ChromeDriver.sameBuild("156.0.8078.74", "156.0.8080.1"), "different build");
        rejects(() -> Download_ChromeDriver.parseVersion("Version not found", "Chrome"));

        StubUpdater updater = new StubUpdater();
        equal("156.0.8078.80", updater.resolveDownload("156.0.8078.74", "win64").version, "exact build");
        check(updater.reads == 1, "exact build needs one lookup");
        updater = new StubUpdater();
        updater.buildJson = "{\"builds\":{}}";
        equal("156.0.8080.10", updater.resolveDownload("156.0.8078.74", "win64").version, "milestone fallback");
        check(updater.reads == 2, "fallback uses second endpoint");
        equal(null, Download_ChromeDriver.findDownload(
                release("builds", "156.0.8078", "156.0.8078.80", "win32"),
                "builds", "156.0.8078", "win64"), "do not select another platform");
        equal(null, Download_ChromeDriver.findDownload("{\"builds\":{}}",
                "builds", "156.0.8078", "win64"), "missing version");
        rejects(() -> Download_ChromeDriver.findDownload("invalid json", "builds", "156.0.8078", "win64"));
        rejects(() -> Download_ChromeDriver.findDownload(
                release("builds", "156.0.8078", "156.0.8078.80", "win64")
                        .replace("storage.googleapis.com", "example.invalid"),
                "builds", "156.0.8078", "win64"));

        Path directory = Files.createTempDirectory("approveproms-driver-test-");
        Path driver = directory.resolve("chromedriver.exe");
        Path extracted = directory.resolve("candidate.exe");
        try {
            byte[] previous = "previous driver".getBytes(StandardCharsets.UTF_8);
            Files.write(driver, previous);
            updater = new StubUpdater();
            updater.ensureCompatible(new File("chrome.exe"), driver.toFile());
            check(updater.reads == 0 && updater.installs == 0, "compatible driver needs no network");

            updater = new StubUpdater();
            updater.installed = "155.0.8059.39";
            updater.ensureCompatible(new File("chrome.exe"), driver.toFile());
            check(updater.installs == 1, "incompatible driver updates");

            updater = new StubUpdater();
            updater.installed = null;
            updater.ensureCompatible(new File("chrome.exe"), driver.toFile());
            check(updater.installs == 1, "unreadable driver updates");

            updater = new StubUpdater();
            updater.ensureCompatible(new File("chrome.exe"), directory.resolve("missing.exe").toFile());
            check(updater.installs == 1, "missing driver downloads");

            updater = new StubUpdater();
            updater.installed = "156.0.8080.10";
            updater.buildJson = "{\"builds\":{}}";
            updater.ensureCompatible(new File("chrome.exe"), driver.toFile());
            check(updater.installs == 0, "installed milestone fallback is reused");

            StubUpdater unavailable = new StubUpdater();
            unavailable.installed = "155.0.8059.39";
            unavailable.networkFailure = true;
            rejects(() -> unavailable.ensureCompatible(new File("chrome.exe"), driver.toFile()));
            check(Arrays.equals(previous, Files.readAllBytes(driver)) && unavailable.installs == 0,
                    "metadata failure preserves previous driver");

            StubUpdater wrongMilestone = new StubUpdater();
            wrongMilestone.installed = "155.0.8059.39";
            wrongMilestone.buildJson = release("builds", "156.0.8078", "157.0.8100.1", "win64");
            rejects(() -> wrongMilestone.ensureCompatible(new File("chrome.exe"), driver.toFile()));
            check(wrongMilestone.installs == 0, "wrong milestone never installed");

            Download_ChromeDriver.extractDriver(new ByteArrayInputStream(zip(
                    "../outside.exe", "ignored", "chromedriver-win64/chromedriver.exe", "driver")),
                    extracted, "win64");
            equal("driver", new String(Files.readAllBytes(extracted), StandardCharsets.UTF_8), "extract only driver");
            try (java.util.stream.Stream<Path> files = Files.list(directory)) {
                check(files.count() == 2, "no other archive paths extracted");
            }
            rejects(() -> Download_ChromeDriver.extractDriver(new ByteArrayInputStream(zip(
                    "../outside.exe", "ignored")), extracted, "win64"));
            rejects(() -> Download_ChromeDriver.extractDriver(new ByteArrayInputStream(zip(
                    "chromedriver-win64/chromedriver.exe", "")), extracted, "win64"));
        } finally {
            Files.deleteIfExists(extracted);
            Files.deleteIfExists(driver);
            Files.deleteIfExists(directory);
        }
        System.out.println("PASS: " + checks + " ChromeDriver regression checks (offline).");
    }

    private static String release(String group, String key, String version, String platform) {
        return "{\"" + group + "\":{\"" + key + "\":{\"version\":\"" + version
                + "\",\"downloads\":{\"chromedriver\":[{\"platform\":\"" + platform
                + "\",\"url\":\"https://storage.googleapis.com/chrome-for-testing-public/"
                + version + "/" + platform + "/chromedriver-" + platform + ".zip\"}]}}}}";
    }

    private static byte[] zip(String... entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (int i = 0; i < entries.length; i += 2) {
                zip.putNextEntry(new ZipEntry(entries[i]));
                zip.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static void equal(Object expected, Object actual, String label) {
        check(expected == null ? actual == null : expected.equals(actual), label);
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
        checks++;
    }

    private static void rejects(Operation operation) throws Exception {
        try {
            operation.run();
            throw new AssertionError("Expected IOException");
        } catch (IOException expected) {
            checks++;
        }
    }

    private interface Operation {
        void run() throws Exception;
    }

    private static final class StubUpdater extends Download_ChromeDriver {
        String buildJson = release("builds", "156.0.8078", "156.0.8078.80", "win64");
        String installed = "156.0.8078.80";
        int reads;
        int installs;
        boolean networkFailure;

        @Override
        public String getChromeVersion(File binary) {
            return "156.0.8078.74";
        }

        @Override
        String getDriverVersion(Path driver) throws IOException {
            if (installed == null) {
                throw new IOException("Simulated broken executable");
            }
            return installed;
        }

        @Override
        String readJson(String url) throws IOException {
            reads++;
            if (networkFailure) {
                throw new IOException("Simulated network failure");
            }
            return url.contains("per-build") ? buildJson
                    : release("milestones", "156", "156.0.8080.10", "win64");
        }

        @Override
        void installDriver(Download download, Path driver) {
            installs++;
        }
    }
}
