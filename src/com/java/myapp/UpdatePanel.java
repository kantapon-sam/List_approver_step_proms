package com.java.myapp;

import java.awt.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import javax.swing.*;

/** Checks in the background on startup and every 30 minutes; installation requires the update button. */
final class UpdatePanel extends JPanel {
    private final JLabel status=new JLabel("Version "+ListApproverProms.VERSION+" • Checking for updates...");
    private final JButton check=new JButton("Check for updates"),install=new JButton("Update and restart");
    private final JFrame frame;
    private final Path home;
    private final BooleanSupplier exporting;
    private final Consumer<String> log;
    private final Consumer<Boolean> busyChanged;
    private final AtomicBoolean busy=new AtomicBoolean();
    private UpdateManager.Release available;
    private final Timer timer;

    UpdatePanel(JFrame frame,Path home,BooleanSupplier exporting,Consumer<String> log,Consumer<Boolean> busyChanged) {
        super(new FlowLayout(FlowLayout.LEFT));
        this.frame=frame; this.home=home; this.exporting=exporting; this.log=log; this.busyChanged=busyChanged;
        add(status); add(check); add(install); install.setVisible(false);
        check.addActionListener(event -> checkNow()); install.addActionListener(event -> install());
        timer=new Timer(30*60*1000,event -> checkNow()); timer.start();
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent event) { timer.stop(); }
        });
        SwingUtilities.invokeLater(this::checkNow);
    }
    boolean busy() { return busy.get(); }
    void refreshControls() { install.setEnabled(!busy.get() && !exporting.getAsBoolean()); }
    private void checkNow() {
        if(busy.get() || !check.isEnabled()) return;
        check.setEnabled(false);
        new SwingWorker<UpdateManager.Release,Void>() {
            @Override protected UpdateManager.Release doInBackground() throws Exception { return UpdateManager.check(); }
            @Override protected void done() {
                check.setEnabled(true);
                if(!frame.isDisplayable()) return;
                try {
                    UpdateManager.Release latest=get();
                    available=latest!=null && latest.newerThan(ListApproverProms.VERSION)?latest:null;
                    install.setVisible(available!=null); refreshControls();
                    status.setForeground(available==null?new Color(30,110,65):new Color(175,85,0));
                    status.setText(available==null?"Version "+ListApproverProms.VERSION+(latest==null?" • No release yet":" • Up to date"):
                        "Version "+ListApproverProms.VERSION+" • Update available: "+available.version);
                } catch(Exception ex) {
                    status.setForeground(Color.GRAY);
                    status.setText("Version "+ListApproverProms.VERSION+" • Update check failed. Please retry.");
                    log.accept("Update check: "+Exporter.message(ex.getCause()==null?ex:ex.getCause()));
                }
                revalidate();
            }
        }.execute();
    }
    private void install() {
        if(available==null || exporting.getAsBoolean() || !busy.compareAndSet(false,true)) return;
        final UpdateManager.Release release=available;
        busyChanged.accept(true); check.setEnabled(false); refreshControls();
        status.setText("Downloading "+release.version+" • The application will restart when ready.");
        new SwingWorker<Void,Void>() {
            @Override protected Void doInBackground() throws Exception {
                Path stage=UpdateManager.stage(home,release,log);
                UpdateManager.launchInstaller(home,stage); return null;
            }
            @Override protected void done() {
                try { get(); frame.dispose(); System.exit(0); }
                catch(Exception ex) {
                    busy.set(false); busyChanged.accept(false); check.setEnabled(true); refreshControls();
                    status.setText("Update failed • Still using version "+ListApproverProms.VERSION);
                    String message=Exporter.message(ex.getCause()==null?ex:ex.getCause()); log.accept("Update: "+message);
                    JOptionPane.showMessageDialog(frame,message,"Update failed",JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
