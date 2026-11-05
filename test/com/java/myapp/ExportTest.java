package com.java.myapp;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class ExportTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        List<Query> q = queries("\uFEFFRef No,Project ID,Steps\r\n26070892,,TK07;TK08\r\n,001234,\r\n");
        equal(2,q.size(),"multiple input types"); equal("001234",q.get(1).project,"leading zero ID");
        check(q.get(0).acceptsStep("NT_IE_TK07"),"short step matches suffix");
        check(!q.get(0).acceptsStep("NT_IE_TK070"),"step is not a substring");
        check(q.get(1).acceptsStep("ANY_STEP"),"blank means every step");
        rejects(() -> queries("Ref No,Project ID,Steps\n,,TK07\n"));
        rejects(() -> queries("Ref No,Project ID,Steps\n26070892,TK07\n"));
        rejects(() -> queries("Ref No,Project ID\n2.607E+07,\n"));
        rejects(() -> queries("Ref No,Request No\n1,2\n"));
        rejects(() -> queries("Ref No,Steps\n1,TK07;\n"));
        rejects(() -> Csv.parse(new StringReader("a,\"unterminated")));
        rejects(() -> Csv.parse(new StringReader("a,\"closed\"oops")));
        equal("a\"b\nc",Csv.parse(new StringReader("\"a\"\"b\nc\",\r\n")).get(0).get(0),"quoted embedded newline");

        StringWriter escaped = new StringWriter();
        Csv.write(escaped,Arrays.asList("ชื่อ, นามสกุล", "He said \"yes\"\nnext", "", "=SUM(1,2)", "@formula", "\tformula"));
        List<String> decoded = Csv.parse(new StringReader(escaped.toString())).get(0);
        equal("ชื่อ, นามสกุล",decoded.get(0),"Thai and comma roundtrip");
        equal("He said \"yes\"\nnext",decoded.get(1),"quotes and newline roundtrip");
        equal("",decoded.get(2),"blank approved date preserved");
        check(decoded.get(3).startsWith("'="),"Excel formula escaped");
        check(decoded.get(4).startsWith("'@"),"at formula escaped");
        check(decoded.get(5).startsWith("'\t"),"leading tab escaped");
        Path temp = Files.createTempFile("approval-output-",".csv");
        try { rejects(() -> Csv.create(temp)); } finally { Files.delete(temp); }

        List<Map<String,String>> requests = Arrays.asList(
            map("refno","26070892","projectname","240258 : Demo"),
            map("refno","260708920","projectname","2402580 : Similar project"),
            map("refno","26070893","projectname","240258 : Demo"));
        equal(1,Exporter.matchingRequests(queries("Ref No\n26070892").get(0),requests).size(),"exact ref matching");
        equal(2,Exporter.matchingRequests(queries("Project ID\n240258").get(0),requests).size(),"project expands to requests, not prefix");
        equal(0,Exporter.matchingRequests(queries("Ref No,Project ID\n26070892,999").get(0),requests).size(),"both IDs constrain match");
        equal("00999",Exporter.projectId(map("projectid","00999","projectname","Demo")),"explicit project column");

        FakePortal portal = new FakePortal();
        StringWriter output = new StringWriter(); Csv.write(output,Exporter.HEADER);
        Exporter exporter = new Exporter(portal,output,"fixture-only",s -> { });
        exporter.run(queries("Ref No,Project ID,Steps\n26070892,,\n404,,\n26070892,,TK08;TK99\n"));
        List<List<String>> report = Csv.parse(new StringReader(output.toString()));
        equal(8,report.size(),"rows include empty/error records and all approvers");
        equal("Approved",report.get(1).get(12),"approval status");
        equal("10/08/2026 14:25:00",report.get(1).get(13),"approved date text retained");
        equal("",report.get(2).get(13),"pending date stays empty");
        equal("NO_APPROVAL_ROWS",report.get(3).get(14),"empty approval table is explicit");
        equal("READ_ERROR",report.get(4).get(14),"failed step is explicit");
        equal("NOT_FOUND_IN_SCOPE",report.get(5).get(14),"not found is scope-qualified");
        equal("STEP_NOT_FOUND",report.get(6).get(14),"one missing requested step is reported");
        equal(3,exporter.problems,"problem count");
        for (List<String> row : report) equal(Exporter.HEADER.size(),row.size(),"rectangular CSV");

        StringWriter projectOutput = new StringWriter();
        Exporter byProject = new Exporter(portal,projectOutput,"fixture-only",s -> { });
        byProject.run(queries("Project ID,Steps\n240258,TK07\n"));
        equal(4,byProject.rows,"all matching requests for project");
        equal(0,byProject.problems,"successful project export");
        portal.cancel = true;
        try { byProject.run(q); throw new AssertionError("cancellation swallowed"); }
        catch (PromsBrowser.Cancelled expected) { checks++; }
        Properties invalid = new Properties(); invalid.setProperty("list.url","https://example.com/proms/pendingapprove");
        rejects(() -> new Settings(invalid));
        Properties shortWait = new Properties(); shortWait.setProperty("timeout.seconds","5"); shortWait.setProperty("quiet.millis","5000");
        rejects(() -> new Settings(shortWait));
        rejects(() -> ListApproverProms.arguments(new String[]{"--input"}));
        rejects(() -> ListApproverProms.arguments(new String[]{"--wat"}));
        System.out.println("PASS: " + checks + " CSV, matching, export, partial failure, cancellation, and settings checks.");
    }

    static List<Query> queries(String text) throws Exception { return Query.parse(Csv.parse(new StringReader(text))); }
    static Map<String,String> map(String... pairs) {
        Map<String,String> values = new LinkedHashMap<>();
        for (int i=0; i<pairs.length; i+=2) values.put(pairs[i],pairs[i+1]); return values;
    }
    private static final class FakePortal implements Portal {
        String search; boolean cancel;
        public void search(String value) { if (cancel) throw new PromsBrowser.Cancelled(); search=value; }
        public void openRequest(String ref) { }
        public List<Map<String,String>> allRows(String kind) {
            if (kind.equals("steps")) return Arrays.asList(map("step","NT_IE_TK07","description","Commissioning"),
                map("step","NT_IE_TK08","description","PAT"), map("step","NT_IE_TK09","description","PAC"));
            if (search.equals("404")) return Collections.emptyList();
            return Arrays.asList(map("refno","26070892","projectname","240258 : Demo"),
                map("refno","26070893","projectname","240258 : Demo"));
        }
        public List<Map<String,String>> approvals(String step) {
            if (step.endsWith("TK09")) throw new IllegalStateException("Approval Line column changed");
            if (step.endsWith("TK08")) return Collections.emptyList();
            return Arrays.asList(map("approver","ผู้อนุมัติ, A","status","Approved","approveddate","10/08/2026 14:25:00"),
                map("approver","Demo B","status","Pending","approveddate",""));
        }
    }
    private interface Attempt { void run() throws Exception; }
    private static void rejects(Attempt action) throws Exception {
        try { action.run(); } catch (Exception expected) { checks++; return; }
        throw new AssertionError("Expected rejection");
    }
    private static void check(boolean condition, String label) { checks++; if (!condition) throw new AssertionError(label); }
    private static void equal(Object expected, Object actual, String label) {
        check(Objects.equals(expected,actual),label + ": expected " + expected + ", got " + actual);
    }
}
