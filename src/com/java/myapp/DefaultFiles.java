package com.java.myapp;

import java.io.*;
import java.nio.file.*;

/** Creates blank configuration files without replacing user data. */
final class DefaultFiles {
    static void initialize(Path home) throws IOException {
        for (String name : new String[]{"Login.txt", "input.csv", "proms.properties"}) {
            Path destination = home.resolve(name);
            if (Files.exists(destination)) continue;
            try (InputStream in = DefaultFiles.class.getResourceAsStream("resources/defaults/" + name)) {
                if (in == null) throw new IOException("Default configuration resource is missing: " + name);
                try { Files.copy(in, destination); }
                catch (FileAlreadyExistsException ignored) { /* Another process created the user's file. */ }
            }
        }
        // Remove only byte-for-byte copies of the three templates distributed with v1.0.0.
        // Edited or locked files remain untouched.
        removeUnchanged(home, "Login.example.txt", "bd5ca7336659af28c9c6155e395a0bca817289992f587e60338ae6b40dabb8f9");
        removeUnchanged(home, "input.example.csv", "3f00dc2c20f3acdfdaf90ab1307bca924136495342fde548954a855ccb4f5cc4");
        removeUnchanged(home, "proms.example.properties", "bc441a0c832cd0e8c43f5fcccefbcbe93132b3638df48693c035a05493a4d250");
    }
    private static void removeUnchanged(Path home, String name, String hash) {
        Path file = home.resolve(name);
        try {
            if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && UpdateManager.sha256(file).equals(hash)) Files.delete(file);
        } catch (IOException ignored) { /* Cleanup must not prevent startup. */ }
    }
}
