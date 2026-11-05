package com.java.myapp;

import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Shows operation progress only. Approval details are saved to CSV. */
final class ExportWindow {
    private static final Color BACKGROUND=new Color(244,247,250), INK=new Color(27,45,62), MUTED=new Color(90,108,124), ACCENT=new Color(0,119,117);
    private final Path home;
    private final JFrame frame=new JFrame("List Approver Step PROMs "+ListApproverProms.VERSION);
    private final JTextField input=new JTextField();
    private final JButton browse=new JButton("Choose CSV"),run=new JButton("Run"),stop=new JButton("Stop"),openCsv=new JButton("Open CSV"),openFolder=new JButton("Open output folder");
    private final JLabel state=new JLabel("Ready"),detail=new JLabel("Enter your IDs in input.csv. Chrome opens only for sign-in."),fileLabel=new JLabel("No report yet");
    private final JProgressBar progress=new JProgressBar();
    private final JTextArea log=new JTextArea();
    private final AtomicBoolean cancelled=new AtomicBoolean(),running=new AtomicBoolean();
    private UpdatePanel updates;
    private Path report;

    ExportWindow(Path home) { this.home=home; }

    void show() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch(Exception ignored) { }
        Font regular=new Font("Segoe UI",Font.PLAIN,13);
        for(Object key:Collections.list(UIManager.getDefaults().keys())) if(UIManager.get(key) instanceof javax.swing.plaf.FontUIResource)
            UIManager.put(key,new javax.swing.plaf.FontUIResource(regular));
        JPanel root=new JPanel(new BorderLayout(16,16)); root.setBackground(BACKGROUND); root.setBorder(BorderFactory.createEmptyBorder(22,24,20,24));
        JPanel top=vertical();
        JLabel title=new JLabel("PROMs Approval Export"); title.setFont(regular.deriveFont(Font.BOLD,25)); title.setForeground(INK);
        top.add(title); top.add(Box.createVerticalStrut(5));
        JLabel subtitle=new JLabel("Sign in  /  Run in background  /  Open CSV for approval details"); subtitle.setForeground(MUTED); top.add(subtitle);
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
        stateBlock.setAlignmentX(Component.LEFT_ALIGNMENT); top.add(stateBlock); root.add(top,BorderLayout.NORTH);
        log.setEditable(false); log.setLineWrap(true); log.setWrapStyleWord(true); log.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12)); log.setMargin(new Insets(10,10,10,10));
        JPanel activity=new JPanel(new BorderLayout(0,8)); activity.setOpaque(false);
        JLabel activityTitle=new JLabel("Run status"); activityTitle.setForeground(MUTED); activityTitle.setFont(regular.deriveFont(Font.BOLD));
        activity.add(activityTitle,BorderLayout.NORTH); activity.add(new JScrollPane(log),BorderLayout.CENTER); root.add(activity,BorderLayout.CENTER);
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
        frame.setContentPane(root); SwingUtilities.updateComponentTreeUI(frame); frame.setMinimumSize(new Dimension(820,520));
        Dimension screen=Toolkit.getDefaultToolkit().getScreenSize(); frame.setSize(Math.min(1040,screen.width-50),Math.min(660,screen.height-70)); frame.setLocationRelativeTo(null);
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
        running.set(true); cancelled.set(false); log.setText("");
        report=ListApproverProms.newOutput(home.resolve("output")); final Path output=report;
        fileLabel.setText("Output: "+output); fileLabel.setToolTipText(output.toString()); state.setForeground(ACCENT); setPhase("Starting","Preparing your run..."); progress.setIndeterminate(true); progress.setString("Starting"); refreshControls(); updates.refreshControls();
        RunObserver observer=new RunObserver() {
            public void phase(String title,String message) { SwingUtilities.invokeLater(() -> { if(!cancelled.get()) { setPhase(title,message); progress.setString(title); } }); }
            public void query(int current,int total,String id) { SwingUtilities.invokeLater(() -> { if(!cancelled.get()) { progress.setIndeterminate(false); progress.setMaximum(total); progress.setValue(current-1); progress.setString("Input "+current+" of "+total); setPhase("Running in background","Searching input "+current+" of "+total+": "+id); } }); }
            public void step(String ref,String step) { SwingUtilities.invokeLater(() -> { if(!cancelled.get()) detail.setText("Ref "+ref+"  /  "+step+"  /  Approval Line"); }); }
        };
        new SwingWorker<Integer,Void>() {
            @Override protected Integer doInBackground() throws Exception { return ListApproverProms.export(queries,settings,credentials,output,cancelled::get,ExportWindow.this::appendLog,observer); }
            @Override protected void done() {
                running.set(false); progress.setIndeterminate(false);
                int code;
                try { code=get(); } catch(Exception ex) { code=1; appendLog("ERROR: "+Exporter.message(ex.getCause()==null?ex:ex.getCause())); }
                String title=code==0?"Completed":code==2?"Completed with issues":code==130?"Stopped":"Run failed";
                state.setForeground(code==0?ACCENT:code==1?new Color(180,35,24):new Color(175,91,18));
                setPhase(title,code==0?"Browser closed. CSV saved. Click Open CSV to view approval details.":"Browser closed. This report may be partial. See Run status for details.");
                progress.setString(title); if(code==0 || code==2) progress.setValue(progress.getMaximum());
                if(!Files.isRegularFile(output)) {
                    report=null; fileLabel.setText("No CSV created for this run"); fileLabel.setToolTipText(null);
                    detail.setText("Browser closed. No CSV created. See Run status for details.");
                }
                refreshControls(); updates.refreshControls();
                // Keep the completed run status open. Approval details remain in the CSV.
                notifyRunIssue(code);
            }
        }.execute();
    }

    private void notifyRunIssue(int code) {
        if(code!=1 && code!=2) return;
        String title=code==1?"Run failed":"Completed with issues";
        String message=code==1?"The run could not finish.":"Some inputs or steps could not be read.";
        message+=report==null?"\nNo CSV was created for this run.":"\nThe CSV may be incomplete. Rows already saved have been kept.\n\nCSV: "+report;
        message+="\n\nSee Run status for the error details. Check the connection and sign-in before running again.";
        // Notify once after browser cleanup. Do not interrupt each failed step or a user-requested stop.
        frame.setExtendedState(frame.getExtendedState() & ~Frame.ICONIFIED); frame.toFront();
        JOptionPane.showMessageDialog(frame,message,title,code==1?JOptionPane.ERROR_MESSAGE:JOptionPane.WARNING_MESSAGE);
    }

    private void loadLatest() {
        new SwingWorker<Path,Void>() {
            @Override protected Path doInBackground() throws Exception {
                Path latest=null;
                Path directory=home.resolve("output"); if(!Files.isDirectory(directory)) return null;
                try(DirectoryStream<Path> files=Files.newDirectoryStream(directory,"*.csv")) {
                    for(Path file:files) if(Files.isRegularFile(file) && (latest==null || Files.getLastModifiedTime(file).compareTo(Files.getLastModifiedTime(latest))>0)) latest=file;
                }
                return latest;
            }
            @Override protected void done() {
                if(running.get() || report!=null) return;
                try {
                    Path latest=get(); if(latest==null) return;
                    report=latest; fileLabel.setText("Last CSV: "+latest); fileLabel.setToolTipText(latest.toString()); refreshControls();
                } catch(Exception ex) { appendLog("Could not locate the last CSV: "+Exporter.message(ex.getCause()==null?ex:ex.getCause())); }
            }
        }.execute();
    }
}
