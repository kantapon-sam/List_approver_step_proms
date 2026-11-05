package com.java.myapp;

import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

/** The report window stays open after every run; only automation browsers are closed. */
final class ExportWindow {
    private static final Color BACKGROUND=new Color(244,247,250), INK=new Color(27,45,62), MUTED=new Color(90,108,124), ACCENT=new Color(0,119,117);
    private final Path home;
    private final JFrame frame=new JFrame("List Approver Step PROMs "+ListApproverProms.VERSION);
    private final JTextField input=new JTextField();
    private final JButton browse=new JButton("Choose CSV"),run=new JButton("Read input CSV"),stop=new JButton("Stop"),openCsv=new JButton("Open CSV"),openFolder=new JButton("Open output folder");
    private final JLabel state=new JLabel("Ready"),detail=new JLabel("Enter your IDs in input.csv. Chrome opens only for sign-in."),fileLabel=new JLabel("No report yet"),previewLabel=new JLabel("Results appear here as they are read.");
    private final JLabel[] counts=new JLabel[5];
    private final JProgressBar progress=new JProgressBar();
    private final JTextArea log=new JTextArea();
    private final DefaultTableModel model=new DefaultTableModel(Exporter.HEADER.toArray(),0) {
        @Override public boolean isCellEditable(int row,int column) { return false; }
    };
    private final JTable table=new JTable(model);
    private final JTabbedPane tabs=new JTabbedPane();
    private final AtomicBoolean cancelled=new AtomicBoolean(),running=new AtomicBoolean();
    private UpdatePanel updates;
    private ReportSnapshot summary=new ReportSnapshot();
    private Path report;
    private int issues;

    ExportWindow(Path home) { this.home=home; }

    void show() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch(Exception ignored) { }
        Font regular=new Font("Segoe UI",Font.PLAIN,13);
        for(Object key:Collections.list(UIManager.getDefaults().keys())) if(UIManager.get(key) instanceof javax.swing.plaf.FontUIResource)
            UIManager.put(key,new javax.swing.plaf.FontUIResource(regular));
        JPanel root=new JPanel(new BorderLayout(16,16)); root.setBackground(BACKGROUND); root.setBorder(BorderFactory.createEmptyBorder(22,24,20,24));
        JPanel top=vertical();
        JLabel title=new JLabel("PROMs Approval Report"); title.setFont(regular.deriveFont(Font.BOLD,25)); title.setForeground(INK);
        top.add(title); top.add(Box.createVerticalStrut(5));
        JLabel subtitle=new JLabel("Sign in once  /  Extract in background  /  Review your results"); subtitle.setForeground(MUTED); top.add(subtitle);
        updates=new UpdatePanel(frame,home,running::get,this::appendLog,busy -> refreshControls());
        updates.setOpaque(false); updates.setAlignmentX(Component.LEFT_ALIGNMENT); top.add(updates);
        top.add(Box.createVerticalStrut(8));
        JPanel inputCard=new JPanel(new BorderLayout(10,6)); inputCard.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(218,226,233)),BorderFactory.createEmptyBorder(12,14,12,14))); inputCard.setBackground(Color.WHITE);
        JLabel inputLabel=new JLabel("INPUT CSV"); inputLabel.setForeground(MUTED); inputLabel.setFont(regular.deriveFont(Font.BOLD,11)); inputCard.add(inputLabel,BorderLayout.NORTH);
        input.setText(home.resolve("input.csv").toString()); inputCard.add(input,BorderLayout.CENTER); inputCard.add(browse,BorderLayout.EAST);
        JLabel hint=new JLabel("Ref No or Project ID, one per row. All steps and approval statuses are included."); hint.setForeground(MUTED); inputCard.add(hint,BorderLayout.SOUTH);
        inputCard.setAlignmentX(Component.LEFT_ALIGNMENT); top.add(inputCard); top.add(Box.createVerticalStrut(14));
        JPanel statusRow=new JPanel(new BorderLayout(12,4)); statusRow.setOpaque(false);
        state.setFont(regular.deriveFont(Font.BOLD,17)); state.setForeground(ACCENT); statusRow.add(state,BorderLayout.WEST);
        progress.setStringPainted(true); progress.setString("Ready"); progress.setPreferredSize(new Dimension(230,22)); statusRow.add(progress,BorderLayout.EAST);
        JPanel stateBlock=new JPanel(new BorderLayout(0,5)); stateBlock.setOpaque(false); stateBlock.add(statusRow,BorderLayout.NORTH); detail.setForeground(MUTED); stateBlock.add(detail,BorderLayout.SOUTH);
        stateBlock.setAlignmentX(Component.LEFT_ALIGNMENT); top.add(stateBlock); top.add(Box.createVerticalStrut(12));
        JPanel cards=new JPanel(new GridLayout(1,5,10,0)); cards.setOpaque(false);
        String[] labels={"CSV ROWS","APPROVED","WAITING","NO APPROVER","OTHER STATUS"};
        for(int i=0;i<labels.length;i++) {
            JPanel card=new JPanel(new BorderLayout(0,5)); card.setBackground(Color.WHITE); card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(224,230,236)),BorderFactory.createEmptyBorder(10,14,10,14)));
            JLabel label=new JLabel(labels[i]); label.setFont(regular.deriveFont(Font.BOLD,11)); label.setForeground(MUTED); card.add(label,BorderLayout.NORTH);
            counts[i]=new JLabel("0"); counts[i].setFont(regular.deriveFont(Font.BOLD,27)); counts[i].setForeground(i==2?new Color(174,107,14):INK); card.add(counts[i]); cards.add(card);
        }
        cards.setAlignmentX(Component.LEFT_ALIGNMENT); top.add(cards); root.add(top,BorderLayout.NORTH);
        table.setRowHeight(28); table.setAutoCreateRowSorter(true); table.setFillsViewportHeight(true); table.setShowVerticalLines(false); table.setGridColor(new Color(235,239,243)); table.setSelectionBackground(new Color(211,235,234));
        table.getTableHeader().setReorderingAllowed(false); table.getTableHeader().setFont(regular.deriveFont(Font.BOLD));
        int[] widths={110,100,70,260,150,175}; for(int i=0;i<widths.length;i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        JPanel results=new JPanel(new BorderLayout(0,6)); results.setBackground(Color.WHITE); results.add(new JScrollPane(table)); previewLabel.setBorder(BorderFactory.createEmptyBorder(5,9,5,9)); previewLabel.setForeground(MUTED); results.add(previewLabel,BorderLayout.SOUTH);
        log.setEditable(false); log.setLineWrap(true); log.setWrapStyleWord(true); log.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12)); log.setMargin(new Insets(10,10,10,10));
        tabs.addTab("Results",results); tabs.addTab("Run log",new JScrollPane(log)); root.add(tabs,BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout(0,8)); bottom.setOpaque(false);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0)); actions.setOpaque(false);
        run.setFont(regular.deriveFont(Font.BOLD)); run.setForeground(ACCENT); actions.add(run); actions.add(stop); actions.add(openCsv); actions.add(openFolder); bottom.add(actions,BorderLayout.NORTH);
        fileLabel.setForeground(MUTED); bottom.add(fileLabel,BorderLayout.SOUTH); root.add(bottom,BorderLayout.SOUTH);
        browse.addActionListener(event -> { JFileChooser chooser=new JFileChooser(home.toFile()); chooser.setFileFilter(new FileNameExtensionFilter("CSV files","csv")); if(chooser.showOpenDialog(frame)==JFileChooser.APPROVE_OPTION) input.setText(chooser.getSelectedFile().getAbsolutePath()); });
        run.addActionListener(event -> run()); stop.addActionListener(event -> stop());
        openCsv.addActionListener(event -> { if(report!=null) open(report); }); openFolder.addActionListener(event -> open(home.resolve("output")));
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent event) {
                if(updates.busy()) { appendLog("Please wait for the update to finish."); return; }
                if(running.get()) { stop(); return; }
                frame.dispose(); System.exit(0);
            }
        });
        frame.setContentPane(root); SwingUtilities.updateComponentTreeUI(frame); frame.setMinimumSize(new Dimension(900,630));
        Dimension screen=Toolkit.getDefaultToolkit().getScreenSize(); frame.setSize(Math.min(1120,screen.width-50),Math.min(820,screen.height-70)); frame.setLocationRelativeTo(null);
        refreshControls(); frame.setVisible(true); loadLatest();
    }

    private JPanel vertical() { JPanel panel=new JPanel(); panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS)); panel.setOpaque(false); return panel; }
    private void refreshControls() {
        boolean busy=running.get() || updates!=null && updates.busy(); run.setEnabled(!busy); input.setEnabled(!busy); browse.setEnabled(!busy); stop.setEnabled(running.get() && !cancelled.get()); openCsv.setEnabled(!busy && report!=null);
    }
    private void appendLog(String value) {
        if(!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(() -> appendLog(value)); return; }
        log.append(value+"\n"); log.setCaretPosition(log.getDocument().getLength());
    }
    private void updateCounts() {
        long[] values={summary.rows,summary.approved,summary.waiting,summary.empty,summary.other};
        for(int i=0;i<values.length;i++) counts[i].setText(Long.toString(values[i]));
        previewLabel.setText(summary.rows>ReportSnapshot.PREVIEW_LIMIT?"Showing the first "+ReportSnapshot.PREVIEW_LIMIT+" of "+summary.rows+" rows. Open CSV for the full report.":summary.rows+" rows • All approval statuses included");
    }
    private void setPhase(String title,String message) { state.setText(title); detail.setText(message); }
    private void stop() { cancelled.set(true); setPhase("Stopping","Waiting for the current browser operation to finish. Partial results will be kept."); refreshControls(); appendLog("Stop requested."); }
    private void open(Path path) {
        try { if(path.equals(home.resolve("output"))) Files.createDirectories(path); Desktop.getDesktop().open(path.toFile()); }
        catch(Exception ex) { appendLog("Could not open file: "+Exporter.message(ex)); }
    }
    private void run() {
        if(running.get() || updates.busy()) return;
        final List<Query> queries; final Settings settings; final LoginCredentials credentials;
        try { queries=Query.read(Paths.get(input.getText().trim())); settings=Settings.read(home.resolve("proms.properties")); credentials=LoginCredentials.readOptional(home.resolve("Login.txt")); }
        catch(Exception ex) { JOptionPane.showMessageDialog(frame,Exporter.message(ex),"Check input / settings / Login.txt",JOptionPane.ERROR_MESSAGE); return; }
        running.set(true); cancelled.set(false); issues=0; summary=new ReportSnapshot(); model.setRowCount(0); log.setText(""); updateCounts();
        report=ListApproverProms.newOutput(home.resolve("output")); final Path output=report;
        fileLabel.setText("Output: "+output); fileLabel.setToolTipText(output.toString()); state.setForeground(ACCENT); setPhase("Starting","Preparing your run..."); progress.setIndeterminate(true); progress.setString("Starting"); tabs.setSelectedIndex(1); refreshControls(); updates.refreshControls();
        RunObserver observer=new RunObserver() {
            public void phase(String title,String message) { SwingUtilities.invokeLater(() -> { if(!cancelled.get()) { setPhase(title,message); progress.setString(title); } }); }
            public void query(int current,int total,String id) { SwingUtilities.invokeLater(() -> { if(!cancelled.get()) { progress.setIndeterminate(false); progress.setMaximum(total); progress.setValue(current-1); progress.setString("Input "+current+" of "+total); setPhase("Running in background","Searching input "+current+" of "+total+": "+id); } }); }
            public void step(String ref,String step) { SwingUtilities.invokeLater(() -> { if(!cancelled.get()) detail.setText("Ref "+ref+"  /  "+step+"  /  Approval Line"); }); }
            public void row(List<String> row) { List<String> copy=new ArrayList<>(row); SwingUtilities.invokeLater(() -> { summary.add(copy); if(model.getRowCount()<ReportSnapshot.PREVIEW_LIMIT) model.addRow(copy.toArray()); updateCounts(); }); }
            public void issue() { SwingUtilities.invokeLater(() -> issues++); }
        };
        new SwingWorker<Integer,Void>() {
            @Override protected Integer doInBackground() throws Exception { return ListApproverProms.export(queries,settings,credentials,output,cancelled::get,ExportWindow.this::appendLog,observer); }
            @Override protected void done() {
                running.set(false); progress.setIndeterminate(false);
                int code;
                try { code=get(); } catch(Exception ex) { code=1; appendLog("ERROR: "+Exporter.message(ex.getCause()==null?ex:ex.getCause())); }
                String title=code==0?"Completed":code==2?"Completed with issues":code==130?"Stopped":"Run failed";
                state.setForeground(code==0?ACCENT:new Color(175,91,18));
                setPhase(title,code==0?"Browser closed. "+summary.rows+" rows saved; you can review the results or start another run.":"Browser closed. This report may be partial. See Run log"+(issues>0?" ("+issues+" issues).":"."));
                progress.setString(title); if(code==0 || code==2) progress.setValue(progress.getMaximum());
                if(!Files.isRegularFile(output)) {
                    report=null; fileLabel.setText("No CSV created for this run"); fileLabel.setToolTipText(null);
                    detail.setText("Browser closed. No CSV created. See Run log for details.");
                }
                refreshControls(); updates.refreshControls(); tabs.setSelectedIndex(code==1?1:0);
                // Deliberately keep the window, results and log open. No completion popup or application exit.
            }
        }.execute();
    }

    private void loadLatest() {
        new SwingWorker<ReportSnapshot,Void>() {
            Path latest;
            @Override protected ReportSnapshot doInBackground() throws Exception {
                Path directory=home.resolve("output"); if(!Files.isDirectory(directory)) return null;
                try(DirectoryStream<Path> files=Files.newDirectoryStream(directory,"*.csv")) {
                    for(Path file:files) if(Files.isRegularFile(file) && (latest==null || Files.getLastModifiedTime(file).compareTo(Files.getLastModifiedTime(latest))>0)) latest=file;
                }
                return latest==null?null:ReportSnapshot.read(latest);
            }
            @Override protected void done() {
                if(running.get() || report!=null) return;
                try {
                    ReportSnapshot saved=get(); if(saved==null) return;
                    summary=saved; report=latest; model.setRowCount(0); for(List<String> row:saved.preview) model.addRow(row.toArray()); updateCounts();
                    fileLabel.setText("Last report: "+latest); fileLabel.setToolTipText(latest.toString()); setPhase("Last report","Saved CSV preview. It may include partial results from a stopped run."); refreshControls();
                    appendLog("Previous CSV loaded. Run logs are shown only during the current application session.");
                } catch(Exception ex) { appendLog("Could not load the last report: "+Exporter.message(ex.getCause()==null?ex:ex.getCause())); }
            }
        }.execute();
    }
}
