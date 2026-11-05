package com.java.myapp;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.*;
import java.util.zip.*;
import javax.swing.JOptionPane;

/** Separate Java 8 process. Replaces only application files after the running app releases its lock. */
public final class UpdateInstaller {
    private static final Set<String> ROOT_FILES=new HashSet<>(Arrays.asList(UpdateManager.JAR,"README.md","CHANGELOG.md",
        "Login.example.txt","input.example.csv","proms.example.properties"));
    interface MutationHook { void afterChange(int count) throws IOException; }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ENGLISH);
        javax.swing.JComponent.setDefaultLocale(Locale.ENGLISH);
        Path home=null,stage=null;
        boolean installed=false;
        try {
            if(args.length!=2) throw new IOException("Invalid updater arguments.");
            home=Paths.get(args[0]).toAbsolutePath().normalize(); stage=Paths.get(args[1]).toAbsolutePath().normalize();
            UpdateManager.validateStage(home,stage); UpdateManager.javaExecutable();
            Files.write(stage.resolve("helper.ready"),new byte[]{1},StandardOpenOption.CREATE_NEW);
            AppInstance lock=null; long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
            while(lock==null) {
                try { lock=AppInstance.acquire(home); }
                catch(AppInstance.Busy busy) {
                    if(System.nanoTime()>deadline) throw new IOException("The application did not close; update was not applied.");
                    Thread.sleep(150);
                }
            }
            try(AppInstance held=lock) {
                install(home,stage,null); installed=true; UpdateManager.clearPending(home,stage);
            }
            new ProcessBuilder(UpdateManager.javaExecutable().toString(),"-jar",home.resolve(UpdateManager.JAR).toString())
                .directory(home.toFile()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(home.resolve(".updates/restart.log").toFile())).start();
            System.out.println("Update installed and application restarted.");
        } catch(Exception ex) {
            String message=(installed?"Update installed, but automatic restart failed. Open the JAR again. ":"Update failed. ")+ex.getMessage();
            System.err.println(message);
            if(!java.awt.GraphicsEnvironment.isHeadless()) JOptionPane.showMessageDialog(null,message,"PROMs Update",JOptionPane.ERROR_MESSAGE);
        } finally {
            if(home!=null && stage!=null) try { UpdateManager.clearPending(home,stage); } catch(IOException ignored) { }
        }
    }

    static void install(Path home,Path stage,MutationHook hook) throws IOException {
        UpdateManager.validateStage(home,stage);
        Path plan=stage.resolve("update.properties"); UpdateManager.safePath(home,plan);
        if(Files.size(plan)>16384) throw new IOException("Update plan is too large.");
        UpdateManager.Release release=UpdateManager.parse(Files.readAllBytes(plan));
        Path archive=stage.resolve("release.zip"); UpdateManager.safePath(home,archive); UpdateManager.verify(archive,release);
        Path installedJar=home.resolve(UpdateManager.JAR); UpdateManager.safePath(home,installedJar);
        String existing=jarVersion(installedJar);
        if(UpdateManager.compare(release.version,existing)<=0) throw new IOException("This release is not newer than the installed application.");
        Path work=Files.createTempDirectory(stage,"install-");
        Path incoming=work.resolve("incoming"),backup=work.resolve("backup");
        Set<String> files=extract(archive,incoming,release.version);
        Set<String> existed=new HashSet<>(); List<String> changed=new ArrayList<>();
        Files.createDirectories(backup);
        // Back up every replaceable file before changing any installed file. User files are never included.
        for(String name:files) {
            Path target=home.resolve(name); UpdateManager.safePath(home,target);
            if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)) {
                if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Update target is not a regular file: "+name);
                Path saved=backup.resolve(name); Files.createDirectories(saved.getParent()); Files.copy(target,saved);
                existed.add(name);
            }
        }
        try {
            // The main JAR is last, so it never references libraries that have not yet been copied.
            List<String> order=new ArrayList<>(files); order.remove(UpdateManager.JAR); order.add(UpdateManager.JAR);
            for(String name:order) {
                Path target=home.resolve(name); UpdateManager.safePath(home,target);
                changed.add(name); replace(incoming.resolve(name),target);
                if(hook!=null) hook.afterChange(changed.size());
            }
            if(!jarVersion(installedJar).equals(release.version)) throw new IOException("Installed version verification failed.");
            Files.write(work.resolve("installed.ok"),release.version.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch(IOException | RuntimeException failure) {
            boolean recovered=true; Collections.reverse(changed);
            for(String name:changed) {
                try {
                    Path target=home.resolve(name); UpdateManager.safePath(home,target);
                    if(existed.contains(name)) replace(backup.resolve(name),target); else Files.deleteIfExists(target);
                } catch(IOException recovery) { recovered=false; failure.addSuppressed(recovery); }
            }
            throw new IOException(recovered?"Update failed; previous application files were restored.":
                "Update failed and rollback was incomplete. Recovery files: "+backup,failure);
        }
    }
    private static void replace(Path source,Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary=Files.createTempFile(target.getParent(),".update-",".tmp");
        try {
            Files.copy(source,temporary,StandardCopyOption.REPLACE_EXISTING);
            if(!UpdateManager.sha256(source).equals(UpdateManager.sha256(temporary))) throw new IOException("Update copy verification failed.");
            // Java 8 on Windows can retain the old JAR handle briefly after releasing the app lock.
            for(int attempt=0;;attempt++) {
                try {
                    try { Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
                    catch(AtomicMoveNotSupportedException ex) { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING); }
                    break;
                } catch(IOException ex) {
                    if(attempt>=30) throw ex;
                    try { Thread.sleep(100); } catch(InterruptedException stopped) { Thread.currentThread().interrupt(); throw new IOException("Update interrupted.",stopped); }
                }
            }
        } finally { Files.deleteIfExists(temporary); }
    }
    static Set<String> extract(Path archive,Path output,String expectedVersion) throws IOException {
        Files.createDirectories(output);
        Set<String> names=new TreeSet<>(),seen=new HashSet<>(); long expanded=0; int entries=0;
        try(ZipInputStream in=new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry; byte[] buffer=new byte[32768];
            while((entry=in.getNextEntry())!=null) {
                if(++entries>2000) throw new IOException("Too many archive entries.");
                String name=entry.getName();
                if(entry.isDirectory()) { if(!name.equals("lib/")) throw new IOException("Unexpected archive directory."); continue; }
                if(!allowed(name) || !seen.add(name.toLowerCase(Locale.ROOT))) throw new IOException("Unsafe or duplicate update entry: "+name);
                Path target=output.resolve(name); UpdateManager.safePath(output,target); Files.createDirectories(target.getParent());
                try(OutputStream out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)) {
                    int n; while((n=in.read(buffer))!=-1) { expanded+=n; if(expanded>128L*1024*1024) throw new IOException("Update extraction limit exceeded."); out.write(buffer,0,n); }
                }
                names.add(name);
            }
        }
        if(!names.contains(UpdateManager.JAR)) throw new IOException("Update package has no application JAR.");
        validateJar(output,names,expectedVersion);
        return names;
    }
    static boolean allowed(String name) { return ROOT_FILES.contains(name) || name.matches("lib/[A-Za-z0-9][A-Za-z0-9._-]*\\.jar"); }
    static String jarVersion(Path jarPath) throws IOException {
        try(JarFile jar=new JarFile(jarPath.toFile())) {
            Manifest manifest=jar.getManifest(); String value=manifest==null?null:manifest.getMainAttributes().getValue("Implementation-Version");
            if(!UpdateManager.validVersion(value)) throw new IOException("Application JAR version is invalid."); return value;
        }
    }
    private static void validateJar(Path directory,Set<String> names,String expectedVersion) throws IOException {
        Path file=directory.resolve(UpdateManager.JAR);
        if(!expectedVersion.equals(jarVersion(file))) throw new IOException("Archive version does not match the update manifest.");
        try(JarFile jar=new JarFile(file.toFile())) {
            Manifest manifest=jar.getManifest();
            if(!"com.java.myapp.ListApproverProms".equals(manifest.getMainAttributes().getValue("Main-Class"))
                    || jar.getJarEntry("com/java/myapp/ListApproverProms.class")==null
                    || jar.getJarEntry("com/java/myapp/UpdateInstaller.class")==null) throw new IOException("Not a PROMs application JAR.");
            String classpath=manifest.getMainAttributes().getValue("Class-Path");
            if(classpath==null || classpath.trim().isEmpty()) throw new IOException("Missing dependency classpath.");
            for(String dependency:classpath.trim().split("\\s+")) {
                if(!dependency.startsWith("lib/") || !allowed(dependency) || !names.contains(dependency)) throw new IOException("Missing/invalid update dependency.");
            }
            Enumeration<JarEntry> entries=jar.entries();
            while(entries.hasMoreElements()) {
                JarEntry entry=entries.nextElement(); if(!entry.getName().endsWith(".class")) continue;
                try(DataInputStream in=new DataInputStream(jar.getInputStream(entry))) {
                    if(in.readInt()!=0xCAFEBABE) throw new IOException("Invalid class in update.");
                    in.readUnsignedShort(); if(in.readUnsignedShort()!=52) throw new IOException("Update is not compiled for Java 8.");
                }
            }
        }
    }
}
