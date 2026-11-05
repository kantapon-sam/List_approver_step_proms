package com.java.myapp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** RFC 4180 quoting, UTF-8 BOM support, and Excel-safe text output. */
final class Csv {
    static List<List<String>> read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(reader);
        }
    }

    static List<List<String>> parse(Reader source) throws IOException {
        PushbackReader in = new PushbackReader(source, 1);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false, closed = false, started = false, first = true;
        int c;
        while ((c = in.read()) != -1) {
            if (first && c == '\uFEFF') { first = false; continue; }
            first = false;
            if (quoted) {
                if (c == '"') {
                    int next = in.read();
                    if (next == '"') field.append('"');
                    else { quoted = false; closed = true; if (next != -1) in.unread(next); }
                } else field.append((char)c);
                continue;
            }
            if (closed && c != ',' && c != '\n' && c != '\r')
                throw new IOException("Unexpected text after a closing CSV quote, record " + (rows.size() + 1));
            if (c == '"') {
                if (started) throw new IOException("Quote inside an unquoted CSV field, record " + (rows.size() + 1));
                quoted = true; started = true;
            } else if (c == ',' || c == '\n' || c == '\r') {
                row.add(field.toString()); field.setLength(0); closed = false; started = false;
                if (c != ',') {
                    if (c == '\r') { int next = in.read(); if (next != '\n' && next != -1) in.unread(next); }
                    rows.add(row); row = new ArrayList<>();
                }
            } else { field.append((char)c); started = true; }
        }
        if (quoted) throw new IOException("Unclosed CSV quote in record " + (rows.size() + 1));
        if (started || closed || !row.isEmpty()) { row.add(field.toString()); rows.add(row); }
        return rows;
    }

    static Writer create(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        writer.write('\uFEFF');
        return writer;
    }

    static void write(Writer writer, List<String> fields) throws IOException {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) writer.write(',');
            String value = fields.get(i) == null ? "" : fields.get(i);
            // Quoting alone does not prevent Excel formula execution.
            String trimmed = value.trim();
            if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0
                    || value.startsWith("\t") || value.startsWith("\r")) value = "'" + value;
            writer.write('"'); writer.write(value.replace("\"", "\"\"")); writer.write('"');
        }
        writer.write("\r\n");
        writer.flush();
    }

    static String key(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
