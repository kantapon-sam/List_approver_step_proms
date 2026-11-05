package com.java.myapp;

interface RunObserver {
    RunObserver NONE = new RunObserver() { };
    default void phase(String title, String detail) { }
    default void query(int current, int total, String id) { }
    default void step(String ref, String step) { }
}
