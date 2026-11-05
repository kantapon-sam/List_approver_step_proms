package com.java.myapp;

import java.util.*;
import javax.swing.table.AbstractTableModel;

/** Compact work metadata only; approval records are never retained in the UI. */
final class RunProgress extends AbstractTableModel implements RunObserver {
    private static final String[] COLUMNS={"Input","Project ID","Ref No","Run status","Current step"};
    private final List<Entry> entries=new ArrayList<>();
    private int input, inputs, offset, count, active=-1, completed, issues, currentCompleted, currentIssues;
    private String label="";
    private boolean discovered, finished;

    void reset() {
        entries.clear(); input=inputs=offset=count=completed=issues=currentCompleted=currentIssues=0;
        active=-1; label=""; discovered=finished=false; fireTableDataChanged();
    }
    @Override public void query(int current,int total,String id) {
        input=current; inputs=total; label=id; offset=entries.size(); count=currentCompleted=currentIssues=0;
        active=-1; discovered=false; finished=false;
    }
    @Override public void found(List<Reference> references) {
        discovered=true; count=references.size();
        for(Reference ref:references) entries.add(new Entry(input,ref));
        if(count>0) fireTableRowsInserted(offset,entries.size()-1);
    }
    @Override public void request(int index,RequestState state) {
        int row=offset+index;
        if(index<0 || index>=count) throw new IllegalArgumentException("Unknown request progress index.");
        Entry entry=entries.get(row);
        completed-=isCompleted(entry.state); issues-=isIssue(entry.state);
        currentCompleted-=isCompleted(entry.state); currentIssues-=isIssue(entry.state);
        entry.state=state;
        completed+=isCompleted(state); issues+=isIssue(state);
        currentCompleted+=isCompleted(state); currentIssues+=isIssue(state);
        active=state==RequestState.RUNNING?row:-1; fireTableRowsUpdated(row,row);
    }
    @Override public void step(String ref,String step) {
        if(active>=0) { Entry entry=entries.get(active); entry.step=step; fireTableRowsUpdated(active,active); }
    }
    void finish(int exitCode) {
        finished=true;
        for(int i=0;i<entries.size();i++) {
            Entry entry=entries.get(i);
            if(entry.state==RequestState.PENDING) entry.state=RequestState.NOT_PROCESSED;
            else if(entry.state==RequestState.RUNNING) entry.state=exitCode==130?RequestState.STOPPED:RequestState.FAILED;
        }
        completed=issues=0;
        for(Entry entry:entries) { completed+=isCompleted(entry.state); issues+=isIssue(entry.state); }
        active=-1; fireTableDataChanged();
    }
    int processed() { return finished?completed+issues:currentCompleted+currentIssues; }
    int total() { return finished?entries.size():count; }
    String activeRef() { return active<0?"":entries.get(active).reference.ref; }
    boolean searching() { return !finished && input>0 && !discovered; }
    String inputText() { return input==0?"No input started":("Input "+input+" of "+inputs+" / "+label); }
    String progressText() {
        if(searching()) return "Finding matching Refs...";
        int total=total(), processed=processed();
        return "Processed "+processed+" / "+total+" Refs"+(total==0?"":" ("+(100L*processed/total)+"%)");
    }
    String summaryText() {
        return "Total found: "+entries.size()+"   /   Completed: "+completed+"   /   With issues: "+issues
            +"   /   Remaining: "+(entries.size()-completed-issues);
    }
    private static int isCompleted(RequestState state) { return state==RequestState.COMPLETED?1:0; }
    private static int isIssue(RequestState state) { return state==RequestState.ISSUES || state==RequestState.FAILED?1:0; }
    @Override public int getRowCount() { return entries.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int column) { return COLUMNS[column]; }
    @Override public Object getValueAt(int row,int column) {
        Entry entry=entries.get(row);
        switch(column) {
            case 0:return entry.input;
            case 1:return entry.reference.project;
            case 2:return entry.reference.ref;
            case 3:return entry.state.label;
            case 4:return entry.step;
            default:throw new IndexOutOfBoundsException();
        }
    }
    private static final class Entry {
        final int input; final Reference reference;
        RequestState state=RequestState.PENDING; String step="";
        Entry(int input,Reference reference) { this.input=input; this.reference=reference; }
    }
}
