package com.java.myapp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.*;
import java.util.zip.*;
import javax.tools.ToolProvider;

public final class UpdateTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        check(UpdateManager.compare("1.10.0","1.9.9")>0,"numeric versions");
        check(UpdateManager.compare("1.0.0","1.0.0")==0,"same version");
        check(UpdateManager.compare("1.0.0","2.0.0")<0,"older release");
        check(!UpdateManager.validVersion("1.0.0-beta"),"prerelease rejected");
        check(!UpdateManager.validVersion("1.000000000000.0"),"unbounded version rejected");
        check(!UpdateInstaller.allowed("Login.txt"),"credentials not update-owned");
        check(!UpdateInstaller.allowed("input.csv"),"user input not update-owned");
        check(!UpdateInstaller.allowed("proms.properties"),"user settings not update-owned");
        check(!UpdateInstaller.allowed("lib/../Login.txt"),"path traversal rejected");
        Path root=Files.createTempDirectory("proms-update-test-");
        try {
            Path home=root.resolve("app"); Files.createDirectories(home.resolve("lib"));
            byte[] stubMain=compileRestartStub(root);
            byte[] oldJar=applicationJar("1.0.0",stubMain),newJar=applicationJar("1.0.1",stubMain);
            byte[] library=emptyJar();
            Files.write(home.resolve(UpdateManager.JAR),oldJar);
            Files.write(home.resolve("lib/test.jar"),library);
            Map<String,byte[]> privateFiles=new LinkedHashMap<>();
            privateFiles.put("Login.txt","User,private-test-user\nPassword,private-test-password\n".getBytes(StandardCharsets.UTF_8));
            privateFiles.put("input.csv","Ref No\n123\n".getBytes(StandardCharsets.UTF_8));
            privateFiles.put("proms.properties","timeout.seconds=55\n".getBytes(StandardCharsets.UTF_8));
            privateFiles.put("output/report.csv","keep this report".getBytes(StandardCharsets.UTF_8));
            for(Map.Entry<String,byte[]> entry:privateFiles.entrySet()) { Path file=home.resolve(entry.getKey()); Files.createDirectories(file.getParent()); Files.write(file,entry.getValue()); }
            Map<String,byte[]> content=new LinkedHashMap<>(); content.put(UpdateManager.JAR,newJar); content.put("lib/test.jar",library);
            content.put("lib/added.jar",library); content.put("README.md","new documentation".getBytes(StandardCharsets.UTF_8));
            byte[] archive=zip(content);
            StubSource source=new StubSource(root,archive,"1.0.1");
            UpdateManager.Release release=UpdateManager.check(source);
            check(release.newerThan("1.0.0"),"new release detected");
            byte[] manifest=source.manifest();
            rejects(() -> UpdateManager.parse(new String(manifest,StandardCharsets.UTF_8).replace("https://github.com/","https://evil.invalid/").getBytes(StandardCharsets.UTF_8)),"foreign origin");
            rejects(() -> UpdateManager.parse(new String(manifest,StandardCharsets.UTF_8).replace("app.version=1.0.1","app.version=1.0.0-beta").getBytes(StandardCharsets.UTF_8)),"bad version metadata");
            check(UpdateManager.check(new UpdateManager.Source() {
                public byte[] manifest() throws IOException { throw new FileNotFoundException(); }
                public void download(String url,Path path,long size) { }
            })==null,"no release yet");
            UpdateManager.Source damaged=new UpdateManager.Source() {
                public byte[] manifest() throws IOException { return source.manifest(); }
                public void download(String url,Path path,long size) throws IOException { Files.write(path,new byte[]{1}); }
            };
            rejects(() -> UpdateManager.stage(home,release,s->{},damaged),"corrupt download");
            check(Arrays.equals(oldJar,Files.readAllBytes(home.resolve(UpdateManager.JAR))),"bad download leaves app unchanged");
            Path stage=UpdateManager.stage(home,release,s->{},source);
            rejects(() -> UpdateInstaller.install(home,stage,count->{ if(count==2) throw new IOException("Injected file failure"); }),"rollback on copy failure");
            check(Arrays.equals(oldJar,Files.readAllBytes(home.resolve(UpdateManager.JAR))),"original JAR restored");
            check(!Files.exists(home.resolve("lib/added.jar")),"new file removed by rollback");
            for(Map.Entry<String,byte[]> entry:privateFiles.entrySet()) check(Arrays.equals(entry.getValue(),Files.readAllBytes(home.resolve(entry.getKey()))),"user data untouched after rollback");
            UpdateInstaller.install(home,stage,null);
            check(UpdateInstaller.jarVersion(home.resolve(UpdateManager.JAR)).equals("1.0.1"),"new version installed");
            for(Map.Entry<String,byte[]> entry:privateFiles.entrySet()) check(Arrays.equals(entry.getValue(),Files.readAllBytes(home.resolve(entry.getKey()))),"user data untouched after success");
            rejects(() -> UpdateInstaller.install(home,stage,null),"equal version not reinstalled");
            for(String bad:Arrays.asList("../outside.txt","/absolute.txt","Login.txt","input.csv","proms.properties","lib/../../outside.jar")) {
                Map<String,byte[]> unsafe=new LinkedHashMap<>(content); unsafe.put(bad,new byte[]{1});
                Path file=Files.createTempFile(root,"bad-",".zip"); Files.write(file,zip(unsafe));
                rejects(() -> UpdateInstaller.extract(file,Files.createTempDirectory(root,"extract-"),"1.0.1"),"unsafe zip "+bad);
            }
            Map<String,byte[]> missing=new LinkedHashMap<>(content); missing.remove("lib/test.jar");
            Path file=root.resolve("missing.zip"); Files.write(file,zip(missing));
            rejects(() -> UpdateInstaller.extract(file,root.resolve("missing"),"1.0.1"),"missing dependency");
            rejects(() -> UpdateManager.validateStage(home,root),"external staging rejected");
            try(AppInstance lock=AppInstance.acquire(home)) { rejects(() -> AppInstance.acquire(home),"concurrent application prevented"); }

            // Exercise actual Java 8 helper handoff, locking, installation and restart with a harmless stub app.
            Files.write(home.resolve(UpdateManager.JAR),oldJar);
            Path lifecycleStage=UpdateManager.stage(home,release,s->{},source);
            Process helper;
            try(AppInstance lock=AppInstance.acquire(home)) {
                helper=UpdateManager.launchInstaller(home,lifecycleStage);
                check(UpdateManager.installPending(home),"handoff marker present");
                check(UpdateInstaller.jarVersion(home.resolve(UpdateManager.JAR)).equals("1.0.0"),"helper waits while application holds lock");
            }
            if(!helper.waitFor(30,TimeUnit.SECONDS)) { helper.destroyForcibly(); throw new AssertionError("helper failed to exit"); }
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            while(!Files.exists(home.resolve("restarted.marker")) && System.nanoTime()<deadline) Thread.sleep(100);
            check(Files.exists(home.resolve("restarted.marker")),"updated JAR restarted");
            check(new String(Files.readAllBytes(home.resolve("restarted.marker")),StandardCharsets.UTF_8).equals("1.8"),"restart uses Java 8");
            check(UpdateInstaller.jarVersion(home.resolve(UpdateManager.JAR)).equals("1.0.1"),"helper installed release");
            check(!UpdateManager.installPending(home),"handoff marker cleared");
            for(Map.Entry<String,byte[]> entry:privateFiles.entrySet()) check(Arrays.equals(entry.getValue(),Files.readAllBytes(home.resolve(entry.getKey()))),"user data preserved through restart");
            System.out.println("PASS: "+checks+" updater checks including real Java 8 helper installation/restart, rollback, archive rejection and user-data preservation.");
        } finally { delete(root); }
    }
    private static byte[] compileRestartStub(Path root) throws IOException {
        Path source=root.resolve("stub/com/java/myapp/ListApproverProms.java"); Files.createDirectories(source.getParent());
        String code="package com.java.myapp; public class ListApproverProms { public static void main(String[] a) throws Exception { java.nio.file.Files.write(java.nio.file.Paths.get(\"restarted.marker\"),System.getProperty(\"java.specification.version\").getBytes(\"UTF-8\")); } }";
        Files.write(source,code.getBytes(StandardCharsets.UTF_8));
        if(ToolProvider.getSystemJavaCompiler().run(null,null,null,"-encoding","UTF-8","-source","1.8","-target","1.8",source.toString())!=0) throw new IOException("Fixture compilation failed.");
        return Files.readAllBytes(source.resolveSibling("ListApproverProms.class"));
    }
    private static byte[] applicationJar(String version,byte[] main) throws Exception {
        Manifest manifest=new Manifest(); Attributes values=manifest.getMainAttributes();
        values.putValue("Manifest-Version","1.0"); values.putValue("Implementation-Version",version);
        values.putValue("Main-Class","com.java.myapp.ListApproverProms"); values.putValue("Class-Path","lib/test.jar");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(JarOutputStream jar=new JarOutputStream(bytes,manifest)) {
            jar.putNextEntry(new JarEntry("com/java/myapp/ListApproverProms.class")); jar.write(main); jar.closeEntry();
            Path classes=Paths.get(UpdateManager.class.getProtectionDomain().getCodeSource().getLocation().toURI()).resolve("com/java/myapp");
            try(DirectoryStream<Path> files=Files.newDirectoryStream(classes,"*.class")) {
                for(Path file:files) {
                    String name=file.getFileName().toString();
                    if(!name.startsWith("UpdateManager") && !name.startsWith("UpdateInstaller") && !name.startsWith("AppInstance")) continue;
                    jar.putNextEntry(new JarEntry("com/java/myapp/"+name)); Files.copy(file,jar); jar.closeEntry();
                }
            }
        }
        return bytes.toByteArray();
    }
    private static byte[] emptyJar() throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); try(JarOutputStream jar=new JarOutputStream(out)) { } return out.toByteArray();
    }
    private static byte[] zip(Map<String,byte[]> entries) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); try(ZipOutputStream zip=new ZipOutputStream(out)) {
            for(Map.Entry<String,byte[]> entry:entries.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry(); }
        } return out.toByteArray();
    }
    private static final class StubSource implements UpdateManager.Source {
        private final byte[] archive,manifest;
        StubSource(Path root,byte[] archive,String version) throws IOException {
            this.archive=archive; Path file=Files.createTempFile(root,"archive-",".zip"); Files.write(file,archive);
            String content="app.version="+version+"\narchive.url="+UpdateManager.REPOSITORY+"/releases/download/v"+version+"/List_approver_step_proms-"+version+".zip\narchive.sha256="+UpdateManager.sha256(file)+"\narchive.size="+archive.length+"\n";
            manifest=content.getBytes(StandardCharsets.UTF_8);
        }
        public byte[] manifest() { return manifest; }
        public void download(String url,Path target,long size) throws IOException { Files.write(target,archive,StandardOpenOption.CREATE_NEW); }
    }
    private interface Attempt { void run() throws Exception; }
    private static void rejects(Attempt action,String label) throws Exception { try { action.run(); } catch(IOException expected) { checks++; return; } throw new AssertionError(label); }
    private static void check(boolean condition,String label) { checks++; if(!condition) throw new AssertionError(label); }
    private static void delete(Path root) throws IOException {
        try(java.util.stream.Stream<Path> files=Files.walk(root)) {
            Path[] paths=files.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
            for(Path path:paths) { for(int attempt=0;;attempt++) { try { Files.deleteIfExists(path); break; } catch(IOException ex) { if(attempt==20) throw ex; try { Thread.sleep(100); } catch(InterruptedException stop) { Thread.currentThread().interrupt(); throw ex; } } } }
        }
    }
}
