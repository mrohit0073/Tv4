package com.tvremote.tv;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.AudioManager;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class RemoteAccessibilityService extends AccessibilityService {
    private static volatile RemoteAccessibilityService INSTANCE;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private PointerView pointer;
    private WindowManager.LayoutParams pointerLp;
    private ServerSocket tcpServer;
    private DatagramSocket udpServer;
    private volatile BufferedWriter clientOut;
    private volatile Socket clientSocket;
    private Thread tcpThread, udpThread;
    private int screenW, screenH;
    private float px, py;
    private volatile boolean running;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    public static boolean isRunning() { return INSTANCE != null; }
    public static boolean isConnected() { return INSTANCE != null && INSTANCE.connected.get(); }

    @Override public void onCreate() {
        super.onCreate();
        INSTANCE = this;
        running = true;
        screenW = getResources().getDisplayMetrics().widthPixels;
        screenH = getResources().getDisplayMetrics().heightPixels;
        px = screenW * 0.50f;
        py = screenH * 0.50f;
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        main.post(this::createPointer);
        startNetwork();
    }

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        main.post(this::createPointer);
    }

    private void createPointer() {
        if (!running || pointer != null || wm == null) return;
        pointer = new PointerView();
        pointerLp = new WindowManager.LayoutParams(
            dp(42), dp(42),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        pointerLp.gravity = Gravity.TOP | Gravity.LEFT;
        pointerLp.x = Math.round(px - dp(4));
        pointerLp.y = Math.round(py - dp(4));
        try { wm.addView(pointer, pointerLp); pointer.setVisibility(View.VISIBLE); } catch (Exception ignored) {}
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void movePointer(float dx, float dy) {
        px = Math.max(0, Math.min(screenW - dp(8), px + dx));
        py = Math.max(0, Math.min(screenH - dp(8), py + dy));
        main.post(() -> {
            if (pointer == null || pointerLp == null) return;
            pointerLp.x = Math.round(px - dp(4));
            pointerLp.y = Math.round(py - dp(4));
            try { wm.updateViewLayout(pointer, pointerLp); } catch (Exception ignored) {}
        });
    }

    private void clickAtCursor() {
        final float cx = px;
        final float cy = py;
        Path path = new Path();
        path.moveTo(cx, cy);
        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, 1);
        dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    private void longClickAtCursor() {
        final float cx = px, cy = py;
        Path path = new Path();
        path.moveTo(cx, cy);
        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, 650);
        dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    private void scrollAtCursor(float dy) {
        float endY = Math.max(0, Math.min(screenH - 1, py + dy * 2.2f));
        Path path = new Path();
        path.moveTo(px, py);
        path.lineTo(px, endY);
        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, 220);
        dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    private AccessibilityNodeInfo focusedNode() {
        AccessibilityNodeInfo n = findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        if (n == null) n = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        return n;
    }

    private void dpad(int direction) {
        AccessibilityNodeInfo cur = focusedNode();
        if (cur == null) return;
        AccessibilityNodeInfo next = cur.focusSearch(direction);
        if (next != null) {
            next.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            next.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
            next.recycle();
        }
        cur.recycle();
    }

    private void activate() {
        AccessibilityNodeInfo cur = focusedNode();
        if (cur != null) {
            boolean ok = cur.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (!ok) ok = cur.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
            cur.recycle();
            if (ok) return;
        }
        clickAtCursor();
    }

    private void setText(String value) {
        AccessibilityNodeInfo n = focusedNode();
        if (n != null && n.isEditable()) {
            Bundle b = new Bundle();
            b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
            n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
        }
        if (n != null) n.recycle();
    }

    private void command(String c) {
        try {
            if (c.equals("PING")) { send("PONG"); return; }
            if (c.equals("MOVE")) return;
            if (c.startsWith("MOVE:")) {
                String[] a = c.substring(5).split(",");
                movePointer(Float.parseFloat(a[0]), Float.parseFloat(a[1]));
                return;
            }
            if (c.equals("CLICK")) { activate(); return; }
            if (c.equals("LONG_CLICK")) { longClickAtCursor(); return; }
            if (c.startsWith("SCROLL:")) { scrollAtCursor(Float.parseFloat(c.substring(7))); return; }
            if (c.startsWith("TEXT:")) { setText(c.substring(5)); return; }
            if (c.equals("BACK")) { performGlobalAction(GLOBAL_ACTION_BACK); return; }
            if (c.equals("HOME")) { performGlobalAction(GLOBAL_ACTION_HOME); return; }
            if (c.equals("RECENTS")) { performGlobalAction(GLOBAL_ACTION_RECENTS); return; }
            if (c.equals("VOL_UP")) { ((AudioManager)getSystemService(AUDIO_SERVICE)).adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); return; }
            if (c.equals("VOL_DOWN")) { ((AudioManager)getSystemService(AUDIO_SERVICE)).adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); return; }
            if (c.equals("MUTE")) { ((AudioManager)getSystemService(AUDIO_SERVICE)).adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI); return; }
            if (c.equals("DPAD_UP")) { dpad(View.FOCUS_UP); return; }
            if (c.equals("DPAD_DOWN")) { dpad(View.FOCUS_DOWN); return; }
            if (c.equals("DPAD_LEFT")) { dpad(View.FOCUS_LEFT); return; }
            if (c.equals("DPAD_RIGHT")) { dpad(View.FOCUS_RIGHT); return; }
            if (c.equals("CURSOR_CENTER")) { px=screenW*.5f; py=screenH*.5f; movePointer(0,0); return; }
            if (c.equals("CURSOR_HIDE")) { main.post(()->{if(pointer!=null)pointer.setVisibility(View.INVISIBLE);}); return; }
            if (c.equals("CURSOR_SHOW")) { main.post(()->{if(pointer!=null)pointer.setVisibility(View.VISIBLE);}); return; }
        } catch (Exception ignored) {}
    }

    private synchronized void send(String s) {
        BufferedWriter w = clientOut;
        if (w == null) return;
        try { w.write(s); w.write('\n'); w.flush(); } catch (Exception e) { closeClient(); }
    }

    private void startNetwork() {
        tcpThread = new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(45456)) {
                tcpServer = ss;
                while (running) {
                    Socket s = ss.accept();
                    closeClient();
                    clientSocket = s;
                    s.setTcpNoDelay(true);
                    clientOut = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
                    connected.set(true);
                    send("TV_READY|" + screenW + "|" + screenH);
                    BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream()));
                    String line;
                    while ((line = r.readLine()) != null && running) command(line);
                    closeClient();
                }
            } catch (Exception ignored) {}
        }, "TVRemote-TCP");
        tcpThread.start();

        udpThread = new Thread(() -> {
            try (DatagramSocket ds = new DatagramSocket(45455)) {
                udpServer = ds;
                ds.setBroadcast(true);
                byte[] buf = new byte[512];
                while (running) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    ds.receive(p);
                    String q = new String(p.getData(), 0, p.getLength());
                    if (q.startsWith("TVREMOTE_DISCOVER_V3")) {
                        String reply = "TVREMOTE_V3|" + localIp() + "|" + android.os.Build.MODEL + "|" + screenW + "|" + screenH;
                        byte[] out = reply.getBytes();
                        ds.send(new DatagramPacket(out, out.length, p.getAddress(), p.getPort()));
                    }
                }
            } catch (Exception ignored) {}
        }, "TVRemote-UDP");
        udpThread.start();
    }

    private String localIp() {
        try {
            for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements();) {
                NetworkInterface n = e.nextElement();
                for (Enumeration<InetAddress> a = n.getInetAddresses(); a.hasMoreElements();) {
                    InetAddress x = a.nextElement();
                    if (!x.isLoopbackAddress() && x instanceof Inet4Address) return x.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "0.0.0.0";
    }

    private synchronized void closeClient() {
        connected.set(false);
        try { if (clientSocket != null) clientSocket.close(); } catch (Exception ignored) {}
        clientSocket = null; clientOut = null;
    }

    @Override public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {}
    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        running=false;
        closeClient();
        try { if(tcpServer!=null)tcpServer.close(); } catch(Exception ignored){}
        try { if(udpServer!=null)udpServer.close(); } catch(Exception ignored){}
        main.post(() -> {
            try { if(pointer!=null && wm!=null)wm.removeView(pointer); } catch(Exception ignored){}
            pointer=null;
        });
        INSTANCE=null;
        super.onDestroy();
    }

    private class PointerView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        public PointerView() { super(RemoteAccessibilityService.this); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        @Override protected void onDraw(Canvas c) {
            float d=getResources().getDisplayMetrics().density;
            p.setColor(Color.WHITE); p.setStyle(Paint.Style.FILL);
            p.setShadowLayer(8*d, 0, 2*d, Color.BLACK);
            Path a=new Path();
            a.moveTo(5*d,4*d); a.lineTo(5*d,34*d); a.lineTo(14*d,26*d);
            a.lineTo(21*d,38*d); a.lineTo(26*d,35*d); a.lineTo(19*d,23*d);
            a.lineTo(31*d,22*d); a.close();
            c.drawPath(a,p);
            p.clearShadowLayer();
        }
    }
}
