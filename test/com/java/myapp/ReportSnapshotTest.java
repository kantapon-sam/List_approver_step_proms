package com.java.myapp;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class ReportSnapshotTest {
    public static void main(String[] args) throws Exception {
        Path report=Files.createTempFile("proms-summary-", ".csv");
        try {
            try(Writer out=Files.newBufferedWriter(report,java.nio.charset.StandardCharsets.UTF_8)) {
                Csv.write(out,Exporter.HEADER);
                for(String status:Arrays.asList("Approved","Wait Approve","Pending","Rejected",""))
                    Csv.write(out,Arrays.asList("001","002","TK07",status.isEmpty()?"":"Fixture approver",status,""));
            }
            ReportSnapshot saved=ReportSnapshot.read(report);
            require(saved.rows==5 && saved.approved==1 && saved.waiting==2 && saved.empty==1 && saved.other==1,"all statuses counted, including unapproved and empty steps");
            require(saved.preview.get(0).get(0).equals("001") && saved.preview.get(0).get(5).isEmpty(),"IDs and blank dates preserved");
            for(int i=0;i<1100;i++) saved.add(Arrays.asList("001","002","TK10","Fixture approver","Wait Approve",""));
            require(saved.rows==1105 && saved.waiting==1102 && saved.preview.size()==ReportSnapshot.PREVIEW_LIMIT,"preview limit does not truncate totals");
            Files.write(report,Arrays.asList("Ref No,Project ID,Step,Approver,Status,Approved Date","001,002"),java.nio.charset.StandardCharsets.UTF_8);
            try { ReportSnapshot.read(report); throw new AssertionError("incomplete report accepted"); } catch(IOException expected) { }
            Files.write(report,Arrays.asList("Ref No,Project ID"),java.nio.charset.StandardCharsets.UTF_8);
            try { ReportSnapshot.read(report); throw new AssertionError("different report accepted"); } catch(IOException expected) { }
            System.out.println("PASS: saved report, all status counts, limited preview, preserved IDs/dates and incomplete report rejection.");
        } finally { Files.deleteIfExists(report); }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
