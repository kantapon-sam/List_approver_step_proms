package com.java.myapp;

import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.*;

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
            + "Enter Ref No or Project ID in input.csv, then click Read input CSV. Steps are read automatically.\n"
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
        WebDriver driver = null;
        Consumer<String> screenLog = log;
        Path logFile = output.resolveSibling(output.getFileName().toString() + ".log");
        try (Writer writer = Csv.create(output);
             Writer diagnostic = Files.newBufferedWriter(logFile,java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            log = value -> {
                screenLog.accept(value);
                try { diagnostic.write(value + System.lineSeparator()); diagnostic.flush(); }
                catch (IOException ex) { throw new UncheckedIOException(ex); }
            };
            Csv.write(writer, Exporter.HEADER);
            log.accept("Log: " + logFile.toAbsolutePath());
            try {
                log.accept("Checking ChromeDriver...");
                File chrome = prepareDriver();
                if (stop.getAsBoolean()) throw new PromsBrowser.Cancelled();
                ChromeOptions options = new ChromeOptions(); options.setBinary(chrome);
                driver = new ChromeDriver(options);
                driver.manage().timeouts().pageLoadTimeout(settings.timeoutSeconds, TimeUnit.SECONDS);
                driver.manage().timeouts().setScriptTimeout(settings.timeoutSeconds, TimeUnit.SECONDS);
                driver.manage().window().maximize();
                PromsBrowser portal = new PromsBrowser(driver,settings,stop,log);
                log.accept(credentials.available() ? "User/Password will be filled from Login.txt. Click Sign in in Chrome and complete OTP/CAPTCHA if required."
                    : "Enter your User/Password and sign in to PROMs in Chrome.");
                log.accept("Waiting up to " + settings.loginSeconds + " seconds for sign-in.");
                portal.login(credentials);
                log.accept("Signed in. Reading " + settings.listUrl);
                Exporter exporter = new Exporter(portal,writer,settings.listUrl,log);
                log.accept("Reading " + queries.size() + " input rows. Searching each ID directly.");
                exporter.run(queries);
                log.accept("Saved: " + output.toAbsolutePath());
                return exporter.problems == 0 ? 0 : 2;
            } catch (PromsBrowser.Cancelled ex) {
                log.accept("CANCELLED: Stopped by user. This CSV is partial; remaining inputs/steps were not processed.");
                log.accept("Cancelled. Partial CSV: " + output.toAbsolutePath()); return 130;
            } catch (Exception ex) {
                log.accept("RUN_ERROR: This CSV may be partial.");
                log.accept("ERROR: " + Exporter.message(ex));
                log.accept("Incomplete CSV: " + output.toAbsolutePath()); return 1;
            }
        } finally {
            if (driver != null) try { driver.quit(); } catch (RuntimeException ignored) { screenLog.accept("The automation Chrome session could not be closed."); }
        }
    }

    private static Path newOutput(Path directory) {
        return directory.resolve("approval_steps_" + LocalDateTime.now(ZoneId.of("Asia/Bangkok"))
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")) + ".csv");
    }

    private static void showWindow() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
        JFrame frame = new JFrame("List Approver Step PROMs " + VERSION);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        JPanel panel = new JPanel(new BorderLayout(12,12));
        panel.setBorder(BorderFactory.createEmptyBorder(18,18,18,18));
        JPanel header = new JPanel(new GridLayout(0,1,4,4));
        JLabel title = new JLabel("PROMs • Approval Line → CSV"); title.setFont(title.getFont().deriveFont(Font.BOLD,22));
        header.add(title);
        header.add(new JLabel("Enter Ref No or Project ID in input.csv, one ID per row. Each ID is searched directly."));
        header.add(new JLabel("All Activity/Milestone steps are read automatically. No Steps column is needed."));
        header.add(new JLabel("Project IDs include all matching filtered pages. Scope: Pending Approval."));
        header.add(new JLabel("Login.txt can fill User/Password. Complete sign-in in Chrome when prompted."));
        JTextField input = new JTextField(appFile("input.csv").toString());
        JButton browse = new JButton("Choose CSV...");
        JPanel fileRow = new JPanel(new BorderLayout(8,0)); fileRow.add(input); fileRow.add(browse,BorderLayout.EAST);
        header.add(fileRow);
        JTextArea logArea = new JTextArea(18,90); logArea.setEditable(false); logArea.setLineWrap(true); logArea.setWrapStyleWord(true);
        panel.add(header,BorderLayout.NORTH); panel.add(new JScrollPane(logArea),BorderLayout.CENTER);
        JButton start = new JButton("Read input CSV"), cancel = new JButton("Stop"), open = new JButton("Open output folder");
        cancel.setEnabled(false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(start); actions.add(cancel); actions.add(open); panel.add(actions,BorderLayout.SOUTH);
        AtomicBoolean stop = new AtomicBoolean(), running = new AtomicBoolean();
        Consumer<String> log = value -> SwingUtilities.invokeLater(() -> { logArea.append(value+"\n"); logArea.setCaretPosition(logArea.getDocument().getLength()); });
        UpdatePanel updates=new UpdatePanel(frame,APP_DIRECTORY,running::get,log,busy -> {
            start.setEnabled(!busy && !running.get());
            browse.setEnabled(!busy && !running.get()); input.setEnabled(!busy && !running.get());
        });
        header.add(updates);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent event) {
                if(updates.busy()) { log.accept("Preparing the update. Please wait..."); return; }
                if (running.get()) { stop.set(true); log.accept("Stopping after the current browser operation..."); }
                else { frame.dispose(); System.exit(0); }
            }
        });
        browse.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser(APP_DIRECTORY.toFile());
            chooser.setFileFilter(new FileNameExtensionFilter("CSV files","csv"));
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) input.setText(chooser.getSelectedFile().getAbsolutePath());
        });
        start.addActionListener(event -> {
            if(updates.busy()) return;
            final List<Query> queries; final Settings settings; final LoginCredentials credentials;
            try { queries = Query.read(Paths.get(input.getText())); settings = Settings.read(appFile("proms.properties")); credentials = LoginCredentials.readOptional(appFile("Login.txt")); }
            catch (Exception ex) { JOptionPane.showMessageDialog(frame,Exporter.message(ex),"Check input CSV / settings / Login.txt",JOptionPane.ERROR_MESSAGE); return; }
            running.set(true); stop.set(false); start.setEnabled(false); cancel.setEnabled(true);
            browse.setEnabled(false); input.setEnabled(false);
            updates.refreshControls();
            Path output = newOutput(appFile("output")); logArea.setText("");
            new SwingWorker<Integer,Void>() {
                @Override protected Integer doInBackground() throws Exception { return export(queries,settings,credentials,output,stop::get,log); }
                @Override protected void done() {
                    running.set(false); start.setEnabled(true); cancel.setEnabled(false);
                    browse.setEnabled(true); input.setEnabled(true);
                    updates.refreshControls();
                    try {
                        int code = get();
                        String message = code == 0 ? "Export complete." : code == 2 ? "Export complete with issues. Check the companion .log file." : "Export stopped before completion. Check the companion .log file; the CSV may be partial.";
                        JOptionPane.showMessageDialog(frame,message + "\n" + output.toAbsolutePath());
                    } catch (Exception ex) { log.accept("ERROR: " + Exporter.message(ex.getCause() == null ? ex : ex.getCause())); }
                }
            }.execute();
        });
        cancel.addActionListener(event -> { stop.set(true); cancel.setEnabled(false); log.accept("Stopping after the current browser operation..."); });
        open.addActionListener(event -> {
            try { Path out=appFile("output"); Files.createDirectories(out); Desktop.getDesktop().open(out.toFile()); }
            catch (Exception ex) { log.accept(Exporter.message(ex)); }
        });
        frame.setContentPane(panel); frame.pack(); frame.setLocationRelativeTo(null); frame.setVisible(true);
    }
}
