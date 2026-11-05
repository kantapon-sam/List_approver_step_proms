package com.java.myapp;

import java.util.List;

interface RunObserver {
    RunObserver NONE = new RunObserver() { };
    default void phase(String title, String detail) { }
    default void query(int current, int total, String id) { }
    default void found(List<Reference> references) { }
    default void request(int index, RequestState state) { }
    default void step(String ref, String step) { }

    final class Reference {
        final String ref, project;
        Reference(String ref,String project) { this.ref=ref; this.project=project; }
    }
    enum RequestState {
        PENDING("Pending"), RUNNING("Running"), COMPLETED("Completed"), ISSUES("Completed with issues"),
        FAILED("Failed"), STOPPED("Stopped"), NOT_PROCESSED("Not processed");
        final String label;
        RequestState(String label) { this.label=label; }
    }
}
