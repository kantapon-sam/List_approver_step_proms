package com.java.myapp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Checks the installed Chrome binary before Selenium creates a session. */
public class Download_ChromeDriver {

    private static final String CFT = "https://googlechromelabs.github.io/chrome-for-testing/";
    private static final Pattern VERSION = Pattern.compile("(?<![0-9.])[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+(?![0-9.])");

    public File findChromeBinary() throws IOException {
        for (String variable : new String[]{"LOCALAPPDATA", "ProgramFiles", "ProgramFiles(x86)"}) {
            String root = System.getenv(variable);
            if (root != null) {
                File binary = new File(root, "Google/Chrome/Application/chrome.exe");
                if (binary.isFile()) {
                    return binary;
                }
            }
        }
        throw new IOException("Google Chrome was not found. Install Chrome and start again.");
    }

    public String getChromeVersion(File chromeBinary) throws IOException {
        ProcessBuilder command = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                "-WindowStyle", "Hidden", "-Command",
                "(Get-Item -LiteralPath $env:APPROVEPROMS_CHROME_BINARY).VersionInfo.ProductVersion");
        command.environment().put("APPROVEPROMS_CHROME_BINARY", chromeBinary.getAbsolutePath());
        return parseVersion(run(command), "Chrome");
    }

    String getDriverVersion(Path driver) throws IOException {
        return parseVersion(run(new ProcessBuilder(driver.toAbsolutePath().toString(), "--version")), "ChromeDriver");
    }

    public void ensureCompatible(File chromeBinary, File driverFile) throws IOException {
        String chromeVersion = getChromeVersion(chromeBinary);
        Path driver = driverFile.toPath().toAbsolutePath();
        String installed = null;
        System.out.println("Chrome: " + chromeVersion);
        if (Files.isRegularFile(driver)) {
            try {
                installed = getDriverVersion(driver);
                System.out.println("ChromeDriver: " + installed);
                if (sameBuild(chromeVersion, installed)) {
                    System.out.println("ChromeDriver is compatible; no download needed.");
                    return;
                }
            } catch (IOException ex) {
                System.out.println("ChromeDriver could not be checked; downloading a replacement.");
            }
        }
        String platform = windowsPlatform();
        System.out.println("Finding a compatible ChromeDriver for " + chromeVersion + "...");
        Download download = resolveDownload(chromeVersion, platform);
        // Milestone fallback is allowed only when no exact build has been published.
        if (!major(chromeVersion).equals(major(download.version))) {
            throw new IOException("The available ChromeDriver does not match this Chrome milestone.");
        }
        if (download.version.equals(installed)) {
            System.out.println("The recommended milestone fallback is already installed; no download needed.");
            return;
        }
        installDriver(download, driver);
        System.out.println("ChromeDriver " + download.version + " installed. Continuing automatically.");
    }

    Download resolveDownload(String chromeVersion, String platform) throws IOException {
        Download download = findDownload(readJson(CFT + "latest-patch-versions-per-build-with-downloads.json"),
                "builds", build(chromeVersion), platform);
        if (download == null) {
            download = findDownload(readJson(CFT + "latest-versions-per-milestone-with-downloads.json"),
                    "milestones", major(chromeVersion), platform);
        }
        if (download == null) {
            throw new IOException("No ChromeDriver download is available for Chrome " + chromeVersion + " (" + platform + ").");
        }
        return download;
    }

    static Download findDownload(String json, String group, String key, String platform) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject versions = root.getAsJsonObject(group);
            if (versions == null || !versions.has(key)) {
                return null;
            }
            JsonObject release = versions.getAsJsonObject(key);
            String version = release.get("version").getAsString();
            if (!VERSION.matcher(version).matches()) {
                throw new IOException("Chrome for Testing returned an invalid version.");
            }
            JsonObject downloads = release.getAsJsonObject("downloads");
            if (downloads == null || !downloads.has("chromedriver")) {
                return null;
            }
            for (JsonElement entry : downloads.getAsJsonArray("chromedriver")) {
                JsonObject asset = entry.getAsJsonObject();
                if (platform.equals(asset.get("platform").getAsString())) {
                    String url = asset.get("url").getAsString();
                    String expected = "https://storage.googleapis.com/chrome-for-testing-public/"
                            + version + "/" + platform + "/chromedriver-" + platform + ".zip";
                    if (!expected.equals(url)) {
                        throw new IOException("Unexpected ChromeDriver download location.");
                    }
                    return new Download(version, platform, url);
                }
            }
            return null;
        } catch (RuntimeException ex) {
            throw new IOException("Could not read Chrome for Testing version metadata.", ex);
        }
    }

    void installDriver(Download download, Path driver) throws IOException {
        Path candidate = Files.createTempFile(driver.getParent(), ".chromedriver-", ".exe");
        try {
            System.out.println("Downloading ChromeDriver " + download.version + "...");
            HttpURLConnection connection = open(download.url);
            try (InputStream input = connection.getInputStream()) {
                extractDriver(input, candidate, download.platform);
            } finally {
                connection.disconnect();
            }
            if (!download.version.equals(getDriverVersion(candidate))) {
                throw new IOException("Downloaded ChromeDriver failed its version check; the previous driver was kept.");
            }
            if (Files.exists(driver)) {
                Files.copy(driver, driver.resolveSibling("chromedriver.exe.bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(candidate, driver, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(candidate, driver, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                throw new IOException("Cannot replace chromedriver.exe. Close the automation using this file and try again.", ex);
            }
        } finally {
            Files.deleteIfExists(candidate);
        }
    }

    static void extractDriver(InputStream input, Path output, String platform) throws IOException {
        String expected = "chromedriver-" + platform + "/chromedriver.exe";
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (expected.equals(entry.getName()) && !entry.isDirectory()) {
                    try (OutputStream target = Files.newOutputStream(output)) {
                        byte[] buffer = new byte[8192];
                        long total = 0;
                        int count;
                        while ((count = zip.read(buffer)) != -1) {
                            total += count;
                            if (total > 100L * 1024 * 1024) {
                                throw new IOException("ChromeDriver download exceeds the expected size.");
                            }
                            target.write(buffer, 0, count);
                        }
                        if (total == 0) {
                            throw new IOException("Downloaded ChromeDriver is empty.");
                        }
                    }
                    return;
                }
            }
        }
        throw new IOException("The download did not contain chromedriver.exe.");
    }

    String readJson(String url) throws IOException {
        HttpURLConnection connection = open(url);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            return json.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "ApproveProms/3.3");
        return connection;
    }

    private static String run(ProcessBuilder command) throws IOException {
        Process process = command.redirectErrorStream(true).start();
        try {
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                throw new IOException("Version check timed out.");
            }
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            if (process.exitValue() != 0) {
                throw new IOException("Version check failed (exit " + process.exitValue() + ").");
            }
            return output.toString();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Version check interrupted.", ex);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    static String parseVersion(String text, String product) throws IOException {
        Matcher match = VERSION.matcher(text);
        if (!match.find()) {
            throw new IOException("Cannot determine " + product + " version.");
        }
        return match.group();
    }

    static boolean sameBuild(String chrome, String driver) {
        return build(chrome).equals(build(driver));
    }

    private static String build(String version) {
        return version.substring(0, version.lastIndexOf('.'));
    }

    private static String major(String version) {
        return version.substring(0, version.indexOf('.'));
    }

    private static String windowsPlatform() {
        return System.getenv("ProgramFiles(x86)") != null || System.getProperty("os.arch").contains("64")
                ? "win64" : "win32";
    }

    static final class Download {
        final String version;
        final String platform;
        final String url;

        Download(String version, String platform, String url) {
            this.version = version;
            this.platform = platform;
            this.url = url;
        }
    }
}
