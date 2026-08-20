package com.tvremote.tv;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class MainActivity extends Activity {
    private LinearLayout root;
    private TextView service;
    private TextView network;
    private TextView pointer;

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView label(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        return t;
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(34), dp(25), dp(34), dp(25));
        root.setBackgroundColor(Color.rgb(7, 10, 15));

        TextView title = label("TV REMOTE V3", 30);
        title.setTypeface(null, 1);
        root.addView(title);

        service = label("", 18);
        root.addView(service);

        network = label("", 18);
        root.addView(network);

        pointer = label("", 18);
        root.addView(pointer);

        Button accessibility = new Button(this);
        accessibility.setText("OPEN ACCESSIBILITY SETTINGS");
        accessibility.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
        root.addView(accessibility);

        Button test = new Button(this);
        test.setText("TEST / CENTER CURSOR");
        test.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                sendLocal("CURSOR_CENTER");
            }
        });
        root.addView(test);

        Button refresh = new Button(this);
        refresh.setText("REFRESH STATUS");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                refreshStatus();
            }
        });
        root.addView(refresh);

        TextView help = label(
            "\nSETUP\n" +
            "1. Enable TV Remote Receiver V3 in Accessibility.\n" +
            "2. Return here and press TEST / CENTER CURSOR.\n" +
            "3. Keep the receiver installed and enabled.\n" +
            "4. Open TV Remote V3 on the phone.\n\n" +
            "Phone and TV must use the same Wi-Fi network.",
            16
        );
        root.addView(help);

        setContentView(root);
        refreshStatus();
    }

    private void sendLocal(final String command) {
        if (!RemoteAccessibilityService.isRunning()) {
            return;
        }

        new Thread(new Runnable() {
            @Override public void run() {
                Socket socket = null;
                BufferedWriter writer = null;
                try {
                    socket = new Socket("127.0.0.1", 45456);
                    writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                    writer.write(command);
                    writer.write('\n');
                    writer.flush();
                } catch (Exception ignored) {
                } finally {
                    try { if (writer != null) writer.close(); } catch (Exception ignored) {}
                    try { if (socket != null) socket.close(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        boolean running = RemoteAccessibilityService.isRunning();
        service.setText("Accessibility: " + (running ? "● ENABLED" : "● NOT ENABLED"));
        service.setTextColor(running ? Color.rgb(0,230,118) : Color.rgb(255,82,82));
        network.setText("Receiver: port 45456  •  " +
            (RemoteAccessibilityService.isConnected() ? "PHONE CONNECTED" : "WAITING FOR PHONE"));
        pointer.setText("Cursor: " + (running ? "READY" : "UNAVAILABLE"));
    }
}
