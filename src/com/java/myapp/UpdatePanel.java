package com.java.myapp;

import java.awt.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import javax.swing.*;

/** Checks in the background on startup and every 30 minutes; installation requires the update button. */
final class UpdatePanel extends JPanel {
    private final JLabel status=new JLabel("รุ่นปัจจุบัน "+ListApproverProms.VERSION+" • กำลังตรวจเวอร์ชัน...");
    private final JButton check=new JButton("ตรวจเวอร์ชัน"),install=new JButton("อัปเดตและเปิดใหม่");
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
                    status.setText(available==null?"รุ่นปัจจุบัน "+ListApproverProms.VERSION+(latest==null?" • ยังไม่มี Release":" • เป็นรุ่นล่าสุดแล้ว"):
                        "รุ่นปัจจุบัน "+ListApproverProms.VERSION+" • มีรุ่นใหม่ "+available.version);
                } catch(Exception ex) {
                    status.setForeground(Color.GRAY);
                    status.setText("รุ่นปัจจุบัน "+ListApproverProms.VERSION+" • ตรวจอัปเดตไม่ได้ ลองใหม่ได้");
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
        status.setText("กำลังดาวน์โหลด "+release.version+" • โปรแกรมจะเปิดใหม่เมื่อเสร็จ");
        new SwingWorker<Void,Void>() {
            @Override protected Void doInBackground() throws Exception {
                Path stage=UpdateManager.stage(home,release,log);
                UpdateManager.launchInstaller(home,stage); return null;
            }
            @Override protected void done() {
                try { get(); frame.dispose(); System.exit(0); }
                catch(Exception ex) {
                    busy.set(false); busyChanged.accept(false); check.setEnabled(true); refreshControls();
                    status.setText("อัปเดตไม่สำเร็จ • ยังใช้รุ่น "+ListApproverProms.VERSION);
                    String message=Exporter.message(ex.getCause()==null?ex:ex.getCause()); log.accept("Update: "+message);
                    JOptionPane.showMessageDialog(frame,message,"อัปเดตไม่สำเร็จ",JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
