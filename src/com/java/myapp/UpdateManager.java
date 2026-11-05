package com.java.myapp;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Public GitHub release feed. Uses no account credentials and performs no automatic installation. */
final class UpdateManager {
    static final String REPOSITORY="https://github.com/kantapon-sam/List_approver_step_proms";
    static final String FEED=REPOSITORY+"/releases/latest/download/update.properties";
    static final String JAR="List_approver_step_proms.jar";
    static final long MAX_ARCHIVE=64L*1024*1024;
    static final class Release {
        final String version, url, sha256;
        final long size;
        Release(String version,String url,String sha256,long size) { this.version=version; this.url=url; this.sha256=sha256; this.size=size; }
        boolean newerThan(String installed) { return compare(version,installed)>0; }
    }
    interface Source {
        byte[] manifest() throws IOException;
        void download(String url,Path target,long size) throws IOException;
    }
    static final class GitHubSource implements Source {
        public byte[] manifest() throws IOException {
            ByteArrayOutputStream out=new ByteArrayOutputStream(); transfer(FEED,out,16384); return out.toByteArray();
        }
        public void download(String url,Path target,long size) throws IOException {
            try (OutputStream out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)) { transfer(url,out,size); }
        }
    }
    static Release check() throws IOException { return check(new GitHubSource()); }
    static Release check(Source source) throws IOException {
        try { return parse(source.manifest()); } catch (FileNotFoundException ex) { return null; }
    }
    static Release parse(byte[] data) throws IOException {
        if (data.length>16384) throw new IOException("Update manifest is too large.");
        Properties p=new Properties(); p.load(new InputStreamReader(new ByteArrayInputStream(data),StandardCharsets.UTF_8));
        String version=p.getProperty("app.version","").trim();
        String url=p.getProperty("archive.url","").trim();
        String sha=p.getProperty("archive.sha256","").trim().toLowerCase(Locale.ROOT);
        long size;
        try { size=Long.parseLong(p.getProperty("archive.size","")); } catch (NumberFormatException ex) { throw new IOException("Invalid update size."); }
        if (!validVersion(version) || !url.equals(REPOSITORY+"/releases/download/v"+version+"/List_approver_step_proms-"+version+".zip")
                || !sha.matches("[a-f0-9]{64}") || size<=0 || size>MAX_ARCHIVE)
            throw new IOException("Invalid GitHub release manifest.");
        return new Release(version,url,sha,size);
    }
    static boolean validVersion(String version) { return version!=null && version.matches("(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})"); }
    static int compare(String left,String right) {
        if (!validVersion(left) || !validVersion(right)) throw new IllegalArgumentException("Invalid application version.");
        String[] a=left.split("\\."),b=right.split("\\.");
        for (int i=0;i<3;i++) { int comparison=Integer.compare(Integer.parseInt(a[i]),Integer.parseInt(b[i])); if (comparison!=0) return comparison; }
        return 0;
    }

    static Path stage(Path home,Release release,Consumer<String> status) throws IOException {
        return stage(home,release,status,new GitHubSource());
    }
    static Path stage(Path home,Release release,Consumer<String> status,Source source) throws IOException {
        Path updates=home.resolve(".updates"); safePath(home,updates); Files.createDirectories(updates);
        Path stage=Files.createTempDirectory(updates,"release-"+release.version+"-");
        status.accept("Downloading version "+release.version+"...");
        source.download(release.url,stage.resolve("release.zip"),release.size);
        verify(stage.resolve("release.zip"),release);
        UpdateInstaller.extract(stage.resolve("release.zip"),stage.resolve("incoming"),release.version);
        Properties p=new Properties(); p.setProperty("app.version",release.version); p.setProperty("archive.url",release.url);
        p.setProperty("archive.sha256",release.sha256); p.setProperty("archive.size",Long.toString(release.size));
        try (Writer writer=Files.newBufferedWriter(stage.resolve("update.properties"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) { p.store(writer,"Verified public release; no credentials"); }
        status.accept("Files verified. Preparing the update...");
        return stage;
    }
    static void verify(Path archive,Release release) throws IOException {
        if (!Files.isRegularFile(archive) || Files.size(archive)!=release.size || !sha256(archive).equals(release.sha256))
            throw new IOException("Update SHA-256/size verification failed. Installed files were not changed.");
    }
    static String sha256(Path file) throws IOException {
        try {
            MessageDigest hash=MessageDigest.getInstance("SHA-256");
            try (InputStream in=Files.newInputStream(file)) { byte[] buffer=new byte[32768]; int n; while((n=in.read(buffer))!=-1) hash.update(buffer,0,n); }
            StringBuilder value=new StringBuilder(); for(byte b:hash.digest()) value.append(String.format(Locale.ROOT,"%02x",b&255)); return value.toString();
        } catch (NoSuchAlgorithmException ex) { throw new IOException(ex); }
    }
    static Process launchInstaller(Path home,Path stage) throws IOException {
        validateStage(home,stage);
        Path helper=stage.resolve("installer.jar"); Files.copy(home.resolve(JAR),helper);
        if (!sha256(home.resolve(JAR)).equals(sha256(helper))) throw new IOException("Update helper copy failed.");
        Path marker=home.resolve(".updates/install.pending"); safePath(home,marker);
        if (installPending(home)) throw new IOException("Another update is already pending.");
        Files.write(marker,stage.getFileName().toString().getBytes(StandardCharsets.UTF_8));
        Path javaBinary=javaExecutable();
        Process process;
        try {
            process=new ProcessBuilder(javaBinary.toString(),"-Djava.awt.headless="+java.awt.GraphicsEnvironment.isHeadless(),"-cp",helper.toString(),"com.java.myapp.UpdateInstaller",home.toString(),stage.toString())
                .directory(home.toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(home.resolve(".updates/update.log").toFile())).start();
        } catch (IOException ex) { Files.deleteIfExists(marker); throw ex; }
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(System.nanoTime()<deadline) {
            if (Files.exists(stage.resolve("helper.ready"))) return process;
            if (!process.isAlive()) { clearPending(home,stage); throw new IOException("Updater did not start. See .updates/update.log."); }
            try { Thread.sleep(100); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IOException("Update handoff interrupted.",ex); }
        }
        // The parent remains running and holds the lock; the helper cannot alter its files.
        process.destroy(); clearPending(home,stage); throw new IOException("Updater startup timed out. Installed files were not changed.");
    }
    static boolean installPending(Path home) throws IOException {
        Path marker=home.resolve(".updates/install.pending"); safePath(home,marker);
        return Files.isRegularFile(marker) && System.currentTimeMillis()-Files.getLastModifiedTime(marker).toMillis()<TimeUnit.MINUTES.toMillis(3);
    }
    static void clearPending(Path home,Path stage) throws IOException {
        Path marker=home.resolve(".updates/install.pending"); safePath(home,marker);
        if (Files.isRegularFile(marker) && Files.size(marker)<256
                && new String(Files.readAllBytes(marker),StandardCharsets.UTF_8).equals(stage.getFileName().toString())) Files.delete(marker);
    }
    static Path javaExecutable() throws IOException {
        if (!"1.8".equals(System.getProperty("java.specification.version"))) throw new IOException("Updates require Java 8.");
        Path java=Paths.get(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"javaw.exe":"java");
        if (!Files.isRegularFile(java)) throw new IOException("Java 8 executable is missing."); return java;
    }
    static void validateStage(Path home,Path stage) throws IOException {
        safePath(home,stage);
        if (!stage.toAbsolutePath().normalize().getParent().equals(home.toAbsolutePath().normalize().resolve(".updates"))
                || !stage.getFileName().toString().startsWith("release-")) throw new IOException("Invalid update staging path.");
    }
    static void safePath(Path home,Path candidate) throws IOException {
        Path base=home.toAbsolutePath().normalize(),path=candidate.toAbsolutePath().normalize();
        if (!path.startsWith(base) || Files.isSymbolicLink(base)) throw new IOException("Unsafe update path.");
        for (Path p=path; p!=null && !p.equals(base); p=p.getParent()) if (Files.isSymbolicLink(p)) throw new IOException("Update path contains a symbolic link.");
    }
    private static boolean trustedDownload(URL url) {
        return "https".equals(url.getProtocol()) && url.getUserInfo()==null && (url.getPort()==-1 || url.getPort()==443)
            && (url.getHost().equals("github.com") && url.getPath().startsWith("/kantapon-sam/List_approver_step_proms/releases/")
                || url.getHost().equals("release-assets.githubusercontent.com"));
    }
    private static void transfer(String address,OutputStream output,long limit) throws IOException {
        URL url=new URL(address); long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(2);
        for(int redirects=0;redirects<6;redirects++) {
            if (!trustedDownload(url)) throw new IOException("Update redirect is outside GitHub release hosting.");
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(10000); connection.setReadTimeout(20000); connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false); connection.setRequestProperty("User-Agent","ListApproverProms-Java8");
            try {
                int code=connection.getResponseCode();
                if(code==301 || code==302 || code==303 || code==307 || code==308) {
                    String location=connection.getHeaderField("Location"); if(location==null) throw new IOException("Missing GitHub redirect.");
                    url=new URL(url,location); continue;
                }
                if(code==404) throw new FileNotFoundException("No published update is available yet.");
                if(code!=200) throw new IOException("GitHub update request failed (HTTP "+code+").");
                if(connection.getContentLengthLong()>limit) throw new IOException("Update exceeds the download limit.");
                try(InputStream in=connection.getInputStream()) {
                    byte[] buffer=new byte[32768]; long total=0; int n;
                    while((n=in.read(buffer))!=-1) {
                        total+=n; if(total>limit || System.nanoTime()>deadline) throw new IOException("Update download size/time limit exceeded.");
                        output.write(buffer,0,n);
                    }
                }
                return;
            } finally { connection.disconnect(); }
        }
        throw new IOException("Too many GitHub release redirects.");
    }
}
