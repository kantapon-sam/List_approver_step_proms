package com.java.myapp;

import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.swing.*;
import org.openqa.selenium.WebDriver;

public final class ListApproverProms {
    public static final String VERSION = readVersion();
    private static final Path APP_DIRECTORY = appDirectory();
    private static AppInstance instance;

    private static String readVersion() {
        Properties values=new Properties();
        try(InputStream in=ListApproverProms.class.getResourceAsStream("resources/version.properties")) {
            if(in==null) throw new IOException("Version resource is missing.");
            values.load(in); String version=values.getProperty("app.version");
            if(!UpdateManager.validVersion(version)) throw new IOException("Invalid application version."); return version;
        } catch(IOException ex) { throw new ExceptionInInitializerError(ex); }
    }
    private static void initializeDefaultFiles() throws IOException {
        DefaultFiles.initialize(APP_DIRECTORY);
    }
    private static void acquireInstance() throws IOException {
        if(UpdateManager.installPending(APP_DIRECTORY)) throw new IOException("An update is being installed. Please wait for the application to restart.");
        instance=AppInstance.acquire(APP_DIRECTORY);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { instance.close(); } catch(IOException ignored) { } },"release-app-lock"));
    }

    static Path appDirectory() {
        try {
            Path location = Paths.get(ListApproverProms.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location) && location.getFileName().toString().endsWith(".jar")) return location.getParent();
        } catch (Exception ex) { throw new IllegalStateException("Cannot locate the application folder.", ex); }
        return Paths.get("").toAbsolutePath().normalize();
    }

    private static Path appFile(String name) { return APP_DIRECTORY.resolve(name); }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ENGLISH);
        JComponent.setDefaultLocale(Locale.ENGLISH);
        if (!"1.8".equals(System.getProperty("java.specification.version"))) {
            String message = "This application requires Java 8.";
            if (!GraphicsEnvironment.isHeadless()) JOptionPane.showMessageDialog(null,message,"Java 8 required",JOptionPane.ERROR_MESSAGE);
            else System.err.println(message);
            System.exit(1); return;
        }
        if (args.length == 0 && !GraphicsEnvironment.isHeadless()) {
            try { acquireInstance(); initializeDefaultFiles(); }
            catch(IOException ex) { JOptionPane.showMessageDialog(null,ex.getMessage(),"PROMs",JOptionPane.ERROR_MESSAGE); System.exit(1); return; }
            SwingUtilities.invokeLater(ListApproverProms::showWindow); return;
        }
        try {
            Map<String,String> options = arguments(args);
            if (options.containsKey("--help")) { usage(); return; }
            if (options.containsKey("--version")) { System.out.println("List Approver Step PROMs " + VERSION); return; }
            if(options.containsKey("--check-update")) {
                UpdateManager.Release latest=UpdateManager.check();
                System.out.println("Installed: "+VERSION+"; latest: "+(latest==null?"no release":latest.version)
                    +"; update available: "+(latest!=null && latest.newerThan(VERSION))); return;
            }
            if (options.containsKey("--check-driver")) { prepareDriver(); return; }
            if (options.containsKey("--check-login")) {
                LoginCredentials credentials = LoginCredentials.readOptional(appFile("Login.txt"));
                System.out.println(credentials.available() ? "Login.txt OK: User/Password configured. Values hidden. No browser started."
                    : "Login.txt: manual sign-in mode. No browser started."); return;
            }
            Path input = options.containsKey("--input") ? Paths.get(options.get("--input")) : appFile("input.csv");
            Path config = options.containsKey("--config") ? Paths.get(options.get("--config")) : appFile("proms.properties");
            List<Query> queries = Query.read(input);
            Settings settings = Settings.read(config);
            if (options.containsKey("--check-input")) {
                System.out.println("Input OK: " + queries.size() + " queries. IDs are retained as text. No browser started."); return;
            }
            if (!options.containsKey("--input")) { usage(); System.exit(1); }
            acquireInstance();
            LoginCredentials credentials = LoginCredentials.readOptional(appFile("Login.txt"));
            Path output = options.containsKey("--output") ? Paths.get(options.get("--output")) : newOutput(appFile("output"));
            int status = export(queries,settings,credentials,output,() -> false,System.out::println);
            System.exit(status);
        } catch (Exception ex) {
            System.err.println("ERROR: " + Exporter.message(ex)); System.exit(1);
        }
    }

    static Map<String,String> arguments(String[] args) {
        Map<String,String> options = new LinkedHashMap<>();
        Set<String> flags = new HashSet<>(Arrays.asList("--help","--version","--check-driver","--check-input","--check-login","--check-update"));
        Set<String> valued = new HashSet<>(Arrays.asList("--input","--output","--config"));
        for (int i=0; i<args.length; i++) {
            String key=args[i];
            if (key.equals("--all")) throw new IllegalArgumentException("--all was removed. Use --input input.csv to search only the requested IDs.");
            if (options.containsKey(key)) throw new IllegalArgumentException("Duplicate option: " + key);
            if (flags.contains(key)) options.put(key,"");
            else if (valued.contains(key) && i+1<args.length && !args[i+1].startsWith("--")) options.put(key,args[++i]);
            else throw new IllegalArgumentException("Unknown option or missing value: " + key);
        }
        return options;
    }

    private static void usage() {
        System.out.println("List Approver Step PROMs " + VERSION + "\n"
            + "Double-click the JAR with Java 8 for the desktop window.\n"
            + "Enter Ref No or Project ID in input.csv, then click Run. Steps are read automatically.\n"
            + "Each ID is searched directly. Project ID lookups include every matching filtered results page.\n"
            + "java -jar List_approver_step_proms.jar --input input.csv [--output output/report.csv] [--config proms.properties]\n"
            + "Options: --check-input, --check-login, --check-driver, --check-update, --version, --help\n"
            + "CSV columns: Ref No,Project ID. Enter either ID per row; multiple IDs use separate rows.\n"
            + "Login.txt beside the JAR fills User/Password; complete sign-in in Chrome. Default scope: Pending Approval.\n"
            + "Exit: 0 complete, 1 run failed, 2 output contains unresolved records, 130 cancelled.");
    }

    private static File prepareDriver() throws IOException {
        Download_ChromeDriver manager = new Download_ChromeDriver();
        File chrome = manager.findChromeBinary();
        File executable = appFile("chromedriver.exe").toFile().getCanonicalFile();
        manager.ensureCompatible(chrome, executable);
        System.setProperty("webdriver.chrome.driver", executable.getAbsolutePath());
        return chrome;
    }

    static int export(List<Query> queries, Settings settings, LoginCredentials credentials, Path output, BooleanSupplier stop, Consumer<String> log) throws IOException {
        return export(queries,settings,credentials,output,stop,log,RunObserver.NONE);
    }

    static int export(List<Query> queries, Settings settings, LoginCredentials credentials, Path output, BooleanSupplier stop, Consumer<String> log, RunObserver observer) throws IOException {
        WebDriver driver = null;
        boolean created = false;
        try {
            try {
                if (stop.getAsBoolean()) throw new PromsBrowser.Cancelled();
                log.accept("Checking ChromeDriver...");
                observer.phase("Starting", "Checking Chrome and preparing sign-in...");
                File chrome = prepareDriver();
                if (stop.getAsBoolean()) throw new PromsBrowser.Cancelled();
                driver = BrowserSession.create(chrome,settings,false);
                PromsBrowser portal = new PromsBrowser(driver,settings,stop,log);
                log.accept(credentials.available() ? "User/Password will be filled from Login.txt. Click Sign in in Chrome and complete OTP/CAPTCHA if required."
                    : "Enter your User/Password and sign in to PROMs in Chrome.");
                log.accept("Waiting up to " + settings.loginSeconds + " seconds for sign-in.");
                observer.phase("Sign in", "Complete Sign in / OTP / CAPTCHA in the Chrome window.");
                portal.login(credentials);
                observer.phase("Starting background mode", "Keeping your signed-in session while closing the login window...");
                log.accept("Signed in. Starting background extraction...");
                driver = BrowserSession.inBackground(driver,() -> BrowserSession.create(chrome,settings,true),settings,stop);
                portal = new PromsBrowser(driver,settings,stop,log);
                observer.phase("Running in background", "Chrome is hidden. Progress and results remain in this window.");
                log.accept("Background session ready. The login window is closed.");
                try (Writer writer = Csv.create(output)) {
                    created = true;
                    Csv.write(writer, Exporter.HEADER);
                    Exporter exporter = new Exporter(portal,writer,settings.listUrl,log,observer);
                    log.accept("Reading " + queries.size() + " input rows. Searching each ID directly.");
                    exporter.run(queries);
                    log.accept("Saved: " + output.toAbsolutePath());
                    return exporter.problems == 0 ? 0 : 2;
                }
            } catch (PromsBrowser.Cancelled ex) {
                log.accept("CANCELLED: Stopped by user. Remaining inputs/steps were not processed.");
                log.accept(created ? "Partial CSV: " + output.toAbsolutePath() : "No CSV created for this run."); return 130;
            } catch (Exception ex) {
                log.accept("RUN_ERROR: " + Exporter.message(ex));
                log.accept(created ? "Incomplete CSV: " + output.toAbsolutePath() : "No CSV created for this run."); return 1;
            }
        } finally {
            observer.phase("Finishing", "Closing the automation browser...");
            if (driver != null) try { driver.quit(); } catch (RuntimeException ignored) { log.accept("The automation Chrome session could not be closed."); }
            log.accept("Browser work finished. Run status remains in the application window. Open CSV for approval details.");
        }
    }

    static Path newOutput(Path directory) {
        return directory.resolve("approval_steps_" + LocalDateTime.now(ZoneId.of("Asia/Bangkok"))
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");
    }

    private static void showWindow() {
        new ExportWindow(APP_DIRECTORY).show();
    }
}
