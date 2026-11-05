package com.java.myapp;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;

/** Held for the entire app lifetime; the updater uses the same lock after app exit. */
final class AppInstance implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    private AppInstance(FileChannel channel, FileLock lock) { this.channel=channel; this.lock=lock; }
    static AppInstance acquire(Path home) throws IOException {
        Path file=home.resolve(".app.lock"); UpdateManager.safePath(home,file);
        FileChannel channel=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        try {
            FileLock lock;
            try { lock=channel.tryLock(); } catch (OverlappingFileLockException ex) { lock=null; }
            if (lock==null) throw new Busy();
            return new AppInstance(channel,lock);
        } catch (IOException | RuntimeException ex) { channel.close(); throw ex; }
    }
    public void close() throws IOException { try { lock.release(); } finally { channel.close(); } }
    static final class Busy extends IOException { Busy() { super("โปรแกรมกำลังทำงานหรืออัปเดตอยู่ กรุณาปิดหน้าต่างเดิมก่อน / Application is already running."); } }
}
