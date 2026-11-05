package com.java.myapp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

final class Query {
    final int record;
    final String ref, project;
    final Set<String> steps;

    Query(int record, String ref, String project, Set<String> steps) {
        this.record = record; this.ref = ref; this.project = project; this.steps = steps;
    }

    String search() { return ref.isEmpty() ? project : ref; }
    String stepText() { return String.join(";", steps); }
    boolean acceptsStep(String value) {
        if (steps.isEmpty()) return true;
        for (String filter : steps) if (matchesStep(filter, value)) return true;
        return false;
    }
    static boolean matchesStep(String filter, String value) {
        String step = value.trim().toUpperCase(Locale.ROOT);
        return step.equals(filter) || filter.matches("TK[0-9]+") && step.endsWith("_" + filter);
    }

    static List<Query> read(Path file) throws IOException { return parse(Csv.read(file)); }
    static List<Query> parse(List<List<String>> csv) throws IOException {
        if (csv.isEmpty()) throw new IOException("Input CSV is empty.");
        Map<String,Integer> columns = new HashMap<>();
        for (int i = 0; i < csv.get(0).size(); i++) {
            String name = Csv.key(csv.get(0).get(i));
            if (name.equals("requestno") || name.equals("referenceno")) name = "refno";
            if (name.equals("step")) name = "steps";
            if (!Arrays.asList("refno", "projectid", "steps").contains(name))
                throw new IOException("Unknown input column: " + csv.get(0).get(i));
            if (columns.put(name, i) != null) throw new IOException("Duplicate input column: " + name);
        }
        if (!columns.containsKey("refno") && !columns.containsKey("projectid"))
            throw new IOException("CSV needs Ref No or Project ID header.");
        List<Query> queries = new ArrayList<>();
        for (int r = 1; r < csv.size(); r++) {
            List<String> row = csv.get(r);
            if (row.stream().allMatch(s -> s.trim().isEmpty())) continue;
            if (row.size() != csv.get(0).size()) throw new IOException("CSV record " + (r+1) + ": incorrect field count.");
            String ref = value(row, columns, "refno"), project = value(row, columns, "projectid");
            if (ref.isEmpty() && project.isEmpty()) throw new IOException("CSV record " + (r+1) + ": enter Ref No or Project ID.");
            for (String id : Arrays.asList(ref, project)) {
                if (!id.isEmpty() && !id.matches("[A-Za-z0-9_-]+"))
                    throw new IOException("CSV record " + (r+1) + ": invalid ID; use text, not Excel scientific notation.");
            }
            Set<String> steps = new LinkedHashSet<>();
            String selection = value(row, columns, "steps").toUpperCase(Locale.ROOT);
            if (!selection.isEmpty() && !selection.equals("*")) {
                for (String step : selection.split("[;|]", -1)) {
                    step = step.trim();
                    if (!step.matches("[A-Z0-9_-]+")) throw new IOException("CSV record " + (r+1) + ": separate Steps with semicolons.");
                    steps.add(step);
                }
            }
            queries.add(new Query(r+1, ref, project, steps));
        }
        if (queries.isEmpty()) throw new IOException("Input CSV has no queries.");
        return queries;
    }
    private static String value(List<String> row, Map<String,Integer> columns, String name) {
        return columns.containsKey(name) ? row.get(columns.get(name)).trim() : "";
    }
}
