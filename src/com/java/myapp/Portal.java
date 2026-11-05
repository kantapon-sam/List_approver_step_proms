package com.java.myapp;

import java.util.List;
import java.util.Map;

interface Portal {
    void search(String value);
    List<Map<String,String>> allRows(String kind);
    List<Map<String,String>> currentRows(String kind);
    void openRequest(String ref);
    List<Map<String,String>> approvals(String step);
}
