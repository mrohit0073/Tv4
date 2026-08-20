package com.tvremote.mobile;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.content.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    RemoteClient client=new RemoteClient();
    TextView conn, tvInfo;
    LinearLayout root;
    int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    GradientDrawable bg(int color,int r){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(r));return g;}
    TextView tv(String s,float size){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    Button key(String s){
        Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setAllCaps(false);b.setPadding(0,0,0,0);b.setBackground(bg(Color.rgb(25,33,45),16));return b;
    }
    void send(String c){client.send(c);}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(7,10,15));getWindow().setNavigationBarColor(Color.rgb(7,10,15));
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(8),dp(14),dp(10));root.setBackgroundColor(Color.rgb(7,10,15));

        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=tv("TV REMOTE",23);title.setTypeface(null,1);header.addView(title,new LinearLayout.LayoutParams(0,dp(50),1));
        conn=tv("●  DISCONNECTED",13);conn.setGravity(Gravity.CENTER);header.addView(conn,new LinearLayout.LayoutParams(dp(150),dp(50)));root.addView(header);

        tvInfo=tv("Searching for TV…",12);tvInfo.setPadding(dp(12),0,dp(12),0);tvInfo.setBackground(bg(Color.rgb(16,23,31),14));root.addView(tvInfo,new LinearLayout.LayoutParams(-1,dp(42)));

        Touchpad pad=new Touchpad(this);
        TextView padHint=tv("TOUCHPAD   •   TAP CLICK   •   LONG PRESS   •   2-FINGER SCROLL",10);padHint.setGravity(Gravity.CENTER);padHint.setTextColor(Color.rgb(145,158,172));
        LinearLayout pw=new LinearLayout(this);pw.setOrientation(LinearLayout.VERTICAL);pw.setPadding(0,dp(8),0,dp(4));pw.addView(pad,new LinearLayout.LayoutParams(-1,0,1));pw.addView(padHint,new LinearLayout.LayoutParams(-1,dp(26)));root.addView(pw,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout nav=new LinearLayout(this);
        Button back=key("←  BACK"),home=key("⌂  HOME"),recent=key("▣  RECENTS"),keyboard=key("⌨  TEXT");
        back.setOnClickListener(v->send("BACK"));home.setOnClickListener(v->send("HOME"));recent.setOnClickListener(v->send("RECENTS"));keyboard.setOnClickListener(v->textDialog());
        for(Button b1:new Button[]{back,home,recent,keyboard})nav.addView(b1,new LinearLayout.LayoutParams(0,dp(52),1));root.addView(nav,new LinearLayout.LayoutParams(-1,dp(58)));

        LinearLayout dpad=new LinearLayout(this);dpad.setOrientation(LinearLayout.VERTICAL);
        Button up=key("▲"),left=key("◀"),ok=key("OK"),right=key("▶"),down=key("▼");
        up.setOnClickListener(v->send("DPAD_UP"));down.setOnClickListener(v->send("DPAD_DOWN"));left.setOnClickListener(v->send("DPAD_LEFT"));right.setOnClickListener(v->send("DPAD_RIGHT"));ok.setOnClickListener(v->send("CLICK"));
        LinearLayout r1=new LinearLayout(this);r1.setGravity(Gravity.CENTER);r1.addView(up,new LinearLayout.LayoutParams(dp(90),dp(48)));
        LinearLayout r2=new LinearLayout(this);r2.setGravity(Gravity.CENTER);r2.addView(left,new LinearLayout.LayoutParams(dp(90),dp(48)));r2.addView(ok,new LinearLayout.LayoutParams(dp(90),dp(48)));r2.addView(right,new LinearLayout.LayoutParams(dp(90),dp(48)));
        LinearLayout r3=new LinearLayout(this);r3.setGravity(Gravity.CENTER);r3.addView(down,new LinearLayout.LayoutParams(dp(90),dp(48)));
        dpad.addView(r1);dpad.addView(r2);dpad.addView(r3);root.addView(dpad,new LinearLayout.LayoutParams(-1,dp(152)));

        LinearLayout media=new LinearLayout(this);
        Button vd=key("VOL −"),mute=key("MUTE"),vu=key("VOL +"),play=key("▶ / ❚❚");
        vd.setOnClickListener(v->send("VOL_DOWN"));mute.setOnClickListener(v->send("MUTE"));vu.setOnClickListener(v->send("VOL_UP"));play.setOnClickListener(v->Toast.makeText(this,"Media play/pause requires app-specific media support on some Google TV builds.",Toast.LENGTH_SHORT).show());
        for(Button b1:new Button[]{vd,mute,vu,play})media.addView(b1,new LinearLayout.LayoutParams(0,dp(50),1));root.addView(media,new LinearLayout.LayoutParams(-1,dp(56)));

        LinearLayout bottom=new LinearLayout(this);Button reconnect=key("⟳  RECONNECT"),center=key("◎  CENTER CURSOR"),hide=key("CURSOR");
        reconnect.setOnClickListener(v->discover());center.setOnClickListener(v->send("CURSOR_CENTER"));hide.setOnClickListener(v->toggleCursor());
        bottom.addView(reconnect,new LinearLayout.LayoutParams(0,dp(50),1));bottom.addView(center,new LinearLayout.LayoutParams(0,dp(50),1));bottom.addView(hide,new LinearLayout.LayoutParams(0,dp(50),1));root.addView(bottom,new LinearLayout.LayoutParams(-1,dp(58)));

        setContentView(root);discover();
    }

    void setConnected(String ip,String name){
        runOnUiThread(()->{conn.setText("●  CONNECTED");conn.setTextColor(Color.rgb(0,230,118));tvInfo.setText("Connected  •  "+name+"  •  "+ip);});
    }

    void discover(){
        client.close();conn.setText("●  SEARCHING");conn.setTextColor(Color.rgb(255,193,7));tvInfo.setText("Looking for TV Remote Receiver V3…");
        new Thread(()->{
            for(int attempt=0;attempt<3;attempt++){
                try(DatagramSocket ds=new DatagramSocket()){
                    ds.setBroadcast(true);ds.setSoTimeout(1200);
                    byte[] q="TVREMOTE_DISCOVER_V3".getBytes();
                    ds.send(new DatagramPacket(q,q.length,InetAddress.getByName("255.255.255.255"),45455));
                    byte[] buf=new byte[1024];DatagramPacket p=new DatagramPacket(buf,buf.length);ds.receive(p);
                    String s=new String(p.getData(),0,p.getLength());
                    if(s.startsWith("TVREMOTE_V3|")){
                        String[] a=s.split("\\|",-1);String ip=a[1],name=a.length>2?a[2]:"Android TV";
                        if(client.connect(ip,45456)){setConnected(ip,name);return;}
                    }
                }catch(Exception ignored){}
            }
            runOnUiThread(()->{conn.setText("●  NOT FOUND");conn.setTextColor(Color.rgb(255,82,82));tvInfo.setText("Same Wi‑Fi required. Check the TV Accessibility service and tap RECONNECT.");});
        }).start();
    }

    void toggleCursor(){
        // Keep explicit show/hide state on the phone.
        send("CURSOR_HIDE");
        Toast.makeText(this,"Cursor hidden. Tap again to show.",Toast.LENGTH_SHORT).show();
    }

    void textDialog(){
        EditText e=new EditText(this);e.setTextColor(Color.WHITE);e.setHintTextColor(Color.GRAY);e.setHint("Type text…");e.setSingleLine(false);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Send text to TV").setView(e).setNegativeButton("Cancel",null).setPositiveButton("SEND",(x,w)->send("TEXT:"+e.getText().toString().replace("\n"," "))).create();
        d.setOnShowListener(x->{e.requestFocus();d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);});d.show();
    }

    class Touchpad extends View{
        float lastX,lastY;long downAt;boolean moved;int fingers;float speed=1.8f;
        Paint p=new Paint(1);
        Touchpad(Context c){super(c);setBackground(bg(Color.rgb(13,19,27),20));p.setColor(Color.rgb(52,70,86));p.setStrokeWidth(dp(1));}
        protected void onDraw(Canvas c){super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f;p.setColor(Color.rgb(45,60,75));c.drawLine(cx-dp(25),cy,cx+dp(25),cy,p);c.drawLine(cx,cy-dp(25),cx,cy+dp(25),p);}
        public boolean onTouchEvent(MotionEvent e){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:lastX=e.getX();lastY=e.getY();downAt=SystemClock.uptimeMillis();moved=false;fingers=1;return true;
                case MotionEvent.ACTION_POINTER_DOWN:fingers=e.getPointerCount();return true;
                case MotionEvent.ACTION_MOVE:
                    if(e.getPointerCount()>=2){float y=e.getY(0);float dy=y-lastY;if(Math.abs(dy)>0.5){send("SCROLL:"+Math.round(dy*2));lastY=y;}return true;}
                    float dx=e.getX()-lastX,dy=e.getY()-lastY;
                    if(Math.abs(dx)+Math.abs(dy)>0.25){send("MOVE:"+Math.round(dx*speed)+","+Math.round(dy*speed));lastX=e.getX();lastY=e.getY();moved=true;}
                    return true;
                case MotionEvent.ACTION_UP:
                    if(!moved){long dur=SystemClock.uptimeMillis()-downAt;if(dur<500)send("CLICK");else send("LONG_CLICK");}
                    return true;
            }
            return true;
        }
    }

    static class RemoteClient{
        Socket socket;BufferedWriter out;volatile boolean alive;
        synchronized boolean connect(String ip,int port){
            try{close();Socket s=new Socket();s.setTcpNoDelay(true);s.connect(new InetSocketAddress(ip,port),3000);socket=s;out=new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));alive=true;send("PING");return true;}catch(Exception e){close();return false;}
        }
        synchronized void send(String s){if(out==null)return;try{out.write(s);out.write('\n');out.flush();}catch(Exception e){close();}}
        synchronized void close(){alive=false;try{if(socket!=null)socket.close();}catch(Exception ignored){}socket=null;out=null;}
    }
}
