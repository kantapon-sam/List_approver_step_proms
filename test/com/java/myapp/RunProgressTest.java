package com.java.myapp;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Business-level progress and incremental output checks; synthetic inputs only. */
public final class RunProgressTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        RunProgress progress=new RunProgress();
        CountingWriter sink=new CountingWriter();
        ProjectPortal portal=new ProjectPortal(progress,sink); portal.failRef="R03";
        Exporter exporter=new Exporter(portal,sink,"fixture",s -> { },progress);
        exporter.run(ExportTest.queries("Project ID\nP-TEST-A\n"));
        progress.finish(2);
        require(progress.getRowCount()==10,"one project lists all ten distinct matching Refs");
        require(progress.progressText().equals("Processed 10 / 10 Refs (100%)"),"progress counts Refs rather than one input row");
        require(progress.summaryText().contains("Completed: 9") && progress.summaryText().contains("With issues: 1"),"failed step is not counted as successfully completed");
        require(progress.getValueAt(2,3).equals("Completed with issues") && progress.getValueAt(9,3).equals("Completed"),"per-Ref outcomes survive continued extraction");
        require(exporter.rows==39 && sink.flushes==39,"page one is flushed even when a later page fails");
        require(portal.legacyCalls==0,"exporter never gathers all approval pages into a list");

        progress=new RunProgress(); sink=new CountingWriter(); portal=new ProjectPortal(progress,sink); portal.cancelRef="R04";
        try { new Exporter(portal,sink,"fixture",s -> { },progress).run(ExportTest.queries("Project ID\nP-TEST-A\n")); throw new AssertionError("cancellation ignored"); }
        catch(PromsBrowser.Cancelled expected) { }
        progress.finish(130);
        require(progress.progressText().equals("Processed 3 / 10 Refs (30%)"),"cancellation preserves actual progress, not 100 percent");
        require(progress.getValueAt(3,3).equals("Stopped") && progress.getValueAt(9,3).equals("Not processed"),"interrupted and unstarted Refs remain visible");

        progress=new RunProgress(); sink=new CountingWriter(); portal=new ProjectPortal(progress,sink);
        new Exporter(portal,sink,"fixture",s -> { },progress).run(ExportTest.queries("Project ID\nP-TEST-A\nP-TEST-B\n"));
        require(progress.progressText().equals("Processed 2 / 2 Refs (100%)"),"each input has its own discovered total");
        progress.finish(0);
        require(progress.getRowCount()==12 && progress.progressText().equals("Processed 12 / 12 Refs (100%)"),"final summary includes both inputs and retains their rows");
        require(progress.getValueAt(10,0).equals(2),"repeated reference across inputs remains distinguishable");

        progress=new RunProgress(); sink=new CountingWriter(); portal=new ProjectPortal(progress,sink);
        new Exporter(portal,sink,"fixture",s -> { },progress).run(ExportTest.queries("Project ID\nP-NOT-FOUND\n")); progress.finish(2);
        require(progress.total()==0 && progress.processed()==0,"no matches does not invent a completed request");

        progress=new RunProgress(); sink=new CountingWriter(); portal=new ProjectPortal(progress,sink);
        Writer broken=new Writer() {
            public void write(char[] chars,int off,int len) throws IOException { throw new IOException("Simulated disk full"); }
            public void flush() { } public void close() { }
        };
        try { new Exporter(portal,broken,"fixture",s -> { },progress).run(ExportTest.queries("Project ID\nP-TEST-A\n")); throw new AssertionError("disk failure ignored"); }
        catch(IOException expected) { }
        progress.finish(1);
        require(progress.getValueAt(0,3).equals("Failed") && progress.getValueAt(1,3).equals("Not processed"),"disk failure stops extraction and preserves remaining task states");

        Path csv=Files.createTempDirectory("proms-stream-").resolve("report.csv");
        try(Writer out=Csv.create(csv)) {
            Csv.write(out,Exporter.HEADER);
            RunProgress diskProgress=new RunProgress();
            ProjectPortal diskPortal=new ProjectPortal(diskProgress,null) {
                @Override public void readApprovals(String step,ApprovalPage page) throws IOException {
                    page.accept(Collections.singletonList(approval()));
                    try(BufferedReader reader=Files.newBufferedReader(csv,java.nio.charset.StandardCharsets.UTF_8)) {
                        require(reader.readLine()!=null && reader.readLine().contains("R01"),"approval row is on disk before the next page is requested");
                    }
                    throw new PromsBrowser.Cancelled();
                }
            };
            try { new Exporter(diskPortal,out,"fixture",s -> { },diskProgress).run(ExportTest.queries("Project ID\nP-TEST-A\n")); }
            catch(PromsBrowser.Cancelled expected) { }
        } finally { Files.deleteIfExists(csv); Files.deleteIfExists(csv.getParent()); }
        System.out.println("PASS: "+checks+" progress/streaming checks: ten-Ref project, per-Ref outcomes, multiple inputs, cancellation, disk errors and immediate CSV writes.");
    }
    private static void require(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    private static Map<String,String> approval() { return ExportTest.map("approver","Synthetic approver","status","Wait Approve","approveddate",""); }
    private static final class CountingWriter extends Writer {
        long characters,flushes;
        public void write(char[] chars,int off,int len) { characters+=len; }
        public void flush() { flushes++; }
        public void close() { }
    }
    private static class ProjectPortal implements Portal {
        final RunProgress progress; final CountingWriter sink;
        String project,ref,failRef="",cancelRef=""; int legacyCalls;
        ProjectPortal(RunProgress progress,CountingWriter sink) { this.progress=progress; this.sink=sink; }
        public void search(String value) { project=value; }
        public List<Map<String,String>> currentRows(String kind) { return allRows(kind); }
        public List<Map<String,String>> allRows(String kind) {
            if(kind.equals("steps")) return Arrays.asList(ExportTest.map("step","NT_IE_TK07"),ExportTest.map("step","NT_IE_TK08"));
            List<Map<String,String>> rows=new ArrayList<>();
            String actual=project.equals("P-TEST-B")?"P-TEST-B":"P-TEST-A";
            for(int i=1;i<=(actual.equals("P-TEST-B")?2:10);i++) rows.add(ExportTest.map("refno",String.format("R%02d",i),"projectid",actual));
            rows.add(rows.get(0)); // Duplicate portal rows must not inflate the total.
            return rows;
        }
        public void openRequest(String value) {
            ref=value;
            require(progress.total()==(project.equals("P-TEST-B")?2:10),"all matches are listed before extraction starts");
        }
        public List<Map<String,String>> approvals(String step) { legacyCalls++; throw new AssertionError("unbounded approval collection used"); }
        public void readApprovals(String step,ApprovalPage page) throws IOException {
            for(int i=0;i<2;i++) {
                if(i==1 && ref.equals(failRef) && step.endsWith("TK08")) throw new IllegalStateException("Simulated connection lost");
                if(i==1 && ref.equals(cancelRef)) throw new PromsBrowser.Cancelled();
                long flushed=sink==null?0:sink.flushes;
                page.accept(Collections.singletonList(approval()));
                if(sink!=null) require(sink.flushes>flushed,"each page reaches and flushes the writer before another page is read");
            }
        }
    }
}
