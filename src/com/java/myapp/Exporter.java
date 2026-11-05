package com.java.myapp;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

final class Exporter {
    static final List<String> HEADER = Arrays.asList("Ref No", "Project ID", "Step", "Approver", "Status", "Approved Date");
    private final Portal portal;
    private final Writer writer;
    private final String source;
    private final Consumer<String> log;
    int rows, problems;

    Exporter(Portal portal, Writer writer, String source, Consumer<String> log) {
        this.portal = portal; this.writer = writer; this.source = source; this.log = log;
    }

    void run(List<Query> queries) throws IOException {
        int completed = 0;
        for (Query query : queries) {
            log.accept("Search " + (++completed) + " of " + queries.size() + ": "
                + (query.ref.isEmpty() ? "Project ID " : "Ref No ") + query.search());
            List<Map<String,String>> requests;
            try {
                portal.search(query.search());
                // A direct Ref No match needs no request pagination. Project IDs may span filtered pages.
                requests = query.ref.isEmpty() ? Collections.emptyList() : matchingRequests(query, portal.currentRows("requests"));
                if (requests.isEmpty()) requests = matchingRequests(query, portal.allRows("requests"));
            } catch (PromsBrowser.Cancelled ex) { throw ex; }
            catch (RuntimeException ex) {
                emit(query, empty(), empty(), empty(), "", "READ_ERROR", message(ex)); continue;
            }
            if (requests.isEmpty()) {
                emit(query, empty(), empty(), empty(), "", "NOT_FOUND_IN_SCOPE",
                    "No exact match in the configured list page. This does not prove the ID is absent from PROMs.");
                continue;
            }
            for (Map<String,String> request : requests) extractRequest(query, request);
        }
        log.accept("Finished: " + rows + " CSV rows; " + problems + " issues. See this log for details.");
    }

    private void extractRequest(Query query, Map<String,String> request) throws IOException {
        List<Map<String,String>> steps;
        try {
            portal.openRequest(get(request,"refno"));
            steps = portal.allRows("steps");
            Set<String> unique = new HashSet<>();
            for (Map<String,String> step : steps) {
                String code = get(step,"step");
                if (code.isEmpty() || !unique.add(code)) throw new IllegalStateException("Missing or duplicate step name; cannot select the detail reliably.");
            }
        } catch (PromsBrowser.Cancelled ex) { throw ex; }
        catch (RuntimeException ex) { emit(query,request,empty(),empty(),"","READ_ERROR",message(ex)); return; }
        if (steps.isEmpty()) { emit(query,request,empty(),empty(),"","NO_STEPS","Activity/Milestone has no rows."); return; }
        for (String filter : query.steps) {
            if (steps.stream().noneMatch(step -> Query.matchesStep(filter, get(step,"step")))) {
                Map<String,String> missing = new HashMap<>(); missing.put("step",filter);
                emit(query,request,missing,empty(),"","STEP_NOT_FOUND","Requested step is not listed in this request.");
            }
        }
        for (Map<String,String> step : steps) {
            if (!query.acceptsStep(get(step,"step"))) continue;
            log.accept("  Ref " + get(request,"refno") + " / " + get(step,"step") + " / Approval Line");
            List<Map<String,String>> approvals;
            try {
                approvals = portal.approvals(get(step,"step"));
                for (Map<String,String> approval : approvals) {
                    if (!approval.containsKey("approver") || !approval.containsKey("status") || !approval.containsKey("approveddate"))
                        throw new IllegalStateException("Approval Line columns are incomplete.");
                }
            } catch (PromsBrowser.Cancelled ex) { throw ex; }
            catch (RuntimeException ex) { emit(query,request,step,empty(),"","READ_ERROR",message(ex)); continue; }
            if (approvals.isEmpty()) emit(query,request,step,empty(),"","NO_APPROVAL_ROWS","Approval Line table is present and empty.");
            int sequence = 0;
            for (Map<String,String> approval : approvals) emit(query,request,step,approval,""+(++sequence),"OK","");
        }
    }

    static List<Map<String,String>> matchingRequests(Query query, List<Map<String,String>> rows) {
        Map<String,Map<String,String>> unique = new LinkedHashMap<>();
        for (Map<String,String> row : rows) {
            String ref = get(row,"refno"), project = projectId(row);
            if (!query.ref.isEmpty() && !query.ref.equals(ref) || !query.project.isEmpty() && !query.project.equals(project)) continue;
            if (ref.isEmpty()) throw new IllegalStateException("A matching request has no reference number.");
            Map<String,String> previous = unique.put(ref,row);
            if (previous != null && !previous.equals(row)) throw new IllegalStateException("Conflicting rows share reference " + ref);
        }
        return new ArrayList<>(unique.values());
    }

    static String projectId(Map<String,String> row) {
        if (!get(row,"projectid").isEmpty()) return get(row,"projectid");
        String projectName = get(row,"projectname");
        int colon = projectName.indexOf(':');
        return colon < 0 ? "" : projectName.substring(0,colon).trim();
    }

    private void emit(Query q, Map<String,String> request, Map<String,String> step, Map<String,String> approval,
            String order, String result, String message) throws IOException {
        if (result.equals("OK") || result.equals("NO_APPROVAL_ROWS")) {
            String stepLabel = get(step,"step").replaceFirst("^.*_(TK[0-9]+)$", "$1");
            Csv.write(writer, Arrays.asList(get(request,"refno"),projectId(request),stepLabel,get(approval,"approver"),get(approval,"status"),get(approval,"approveddate")));
            rows++;
        }
        if (!result.equals("OK")) {
            if (!result.equals("NO_APPROVAL_ROWS")) problems++;
            log.accept("  " + result + " | Input " + q.record + " / Ref " + (get(request,"refno").isEmpty() ? q.ref : get(request,"refno"))
                + " / Project " + (projectId(request).isEmpty() ? q.project : projectId(request)) + " / Step " + get(step,"step") + ": " + message);
        }
    }

    private static Map<String,String> empty() { return Collections.emptyMap(); }
    static String get(Map<String,String> map, String key) { return map.getOrDefault(key,"").trim(); }
    static String message(Throwable ex) {
        String message = ex.getMessage();
        if (message == null || message.trim().isEmpty()) return ex.getClass().getSimpleName();
        // Selenium embeds session capabilities and stack traces; retain only the diagnostic first line.
        String first = message.split("[\\r\\n]",2)[0];
        return first.length() > 400 ? first.substring(0,400) : first;
    }
}
