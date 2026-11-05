package com.java.myapp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

final class ReportSnapshot {
    static final int PREVIEW_LIMIT = 1000;
    final List<List<String>> preview = new ArrayList<>();
    long rows, approved, waiting, empty, other;

    void add(List<String> row) {
        if (row.size() != Exporter.HEADER.size()) throw new IllegalArgumentException("Unexpected report columns.");
        rows++;
        String status = Csv.key(row.get(4));
        if (status.equals("approved")) approved++;
        else if (status.equals("pending") || status.equals("waitapprove") || status.equals("waiting") || status.equals("waitingapproval")) waiting++;
        else if (row.get(3).trim().isEmpty() && row.get(4).trim().isEmpty() && row.get(5).trim().isEmpty()) empty++;
        else other++;
        if (preview.size() < PREVIEW_LIMIT) preview.add(new ArrayList<>(row));
    }

    static ReportSnapshot read(Path file) throws IOException {
        List<List<String>> records = Csv.read(file);
        if (records.isEmpty() || !records.get(0).equals(Exporter.HEADER)) throw new IOException("The saved report uses different columns. Open the CSV to view it.");
        ReportSnapshot result = new ReportSnapshot();
        for (int i=1;i<records.size();i++) {
            if (records.get(i).stream().allMatch(String::isEmpty)) continue;
            if (records.get(i).size()!=Exporter.HEADER.size()) throw new IOException("The saved report contains an incomplete row.");
            result.add(records.get(i));
        }
        return result;
    }
}
