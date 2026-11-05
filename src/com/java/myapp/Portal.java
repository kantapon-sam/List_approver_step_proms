package com.java.myapp;

import java.util.List;
import java.util.Map;
import java.io.IOException;

interface Portal {
    void search(String value);
    List<Map<String,String>> allRows(String kind);
    List<Map<String,String>> currentRows(String kind);
    void openRequest(String ref);
    List<Map<String,String>> approvals(String step);
    default void readApprovals(String step,ApprovalPage sink) throws IOException { sink.accept(approvals(step)); }
    interface ApprovalPage { void accept(List<Map<String,String>> rows) throws IOException; }
}
