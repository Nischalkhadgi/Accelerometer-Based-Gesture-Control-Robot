package com.abgcr.android.accelerometerbasedgesturecontrolrobot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private float Sensor_MINVAL_PITCH = 1.5f, Sensor_MINVAL_ROLL = 1.5f;
    public Socket client = null;
    public int SocketTimeOut =5000;
    public String IP;
    public int Port;

    private EditText IpTxt, PortTxt;
    private Button ConnectBtn;

    SensorManager sensorManager;
    String CurrentRoverState = "S"; //Default S=Stop, F=Forward, B=Backward, R=Right,L=Left
    boolean CurrentRoverMotionState = false;
    RelativeLayout GLayout;

    GraphicsView graphicsView;

    boolean ISCONNECTEDTOSERVER = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Remove title bar
        //this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        //Set content view
        setContentView(R.layout.activity_main);

        //Set Title
        this.setTitle("Control Room");

        //Init View
        this.IpTxt = (EditText)findViewById(R.id.SERVERTXT);
        this.PortTxt = (EditText)findViewById(R.id.PORTTXT);
        this.ConnectBtn = (Button) findViewById(R.id.CONNECTBTN);
        this.GLayout = (RelativeLayout)findViewById(R.id.GraphicsLayout);

        //Init Graphics View
        graphicsView = new GraphicsView(this);
        this.GLayout.addView(graphicsView);
    }
    @Override
    protected void onResume() {
        //Register Sensor Listener
        sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
        super.onResume();
    }
    @Override
    protected void onStop() {
        super.onStop();
        sensorManager.unregisterListener(this);
    }
    public void ConnectToServer(View v){
       try{
           //input from user
           this.IP = IpTxt.getText().toString().trim();
           this.Port = Integer.parseInt(PortTxt.getText().toString().trim());

           ConnectBtn.setText("Connecting..");
           ConnectBtn.setEnabled(false);

           //requesting connection to server
           if(client == null || client.isConnected() == false){
               //Start Connecting
               new Thread(new Runnable() {
                   @Override
                   public void run() {
                       try{
                           client = new Socket();
                           client.connect(new InetSocketAddress(IP, Port), SocketTimeOut);
                           runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ConnectBtn.setText("DisConnect");
                                    ConnectBtn.setEnabled(true);
                                    ISCONNECTEDTOSERVER =true;
                                }
                            });
                       }
                       catch (final Exception ex){
                           runOnUiThread(new Runnable() {
                               @Override
                               public void run() {
                                    ResetUI();
                                    Toast.makeText(MainActivity.this, ex.getMessage(), Toast.LENGTH_SHORT).show();
                               }
                           });
                           Log.e("1-------->", ex.toString());
                       }
                   }
               }).start();
           }
           else{
               //Close Connection
               ResetUI();
               if(client !=null){
                   client.close();
                   client = null;
               }
           }
       }
       catch (Exception ex){
           Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
           ResetUI();

           Log.e("2-------->", ex.toString());
       }
    }
    private void ResetUI(){
        ConnectBtn.setText("Connect");
        ConnectBtn.setEnabled(true);
        ISCONNECTEDTOSERVER =false;
    }
    public void SendStreamMessage(final String message){
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    if(client != null && client.isConnected()){
                        OutputStream oStream = client.getOutputStream();
                        byte[] buffer = message.getBytes();
                        oStream.write(buffer);
                    }
                    else{
                        Log.d("NO SIGNAL SEND:", message);
                    }
                    //Log.d("---------->","data send");
                }
                catch (Exception ex){
                    //Toast.makeText(this,ex.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("------>", ex.toString());
                }
            }
        });
        t.start();
    }
    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            //Accelerometer selected
            float X = event.values[0] , Y = event.values[1];
            //Current Rover State
            String RoverState;
            if(Y < -Sensor_MINVAL_PITCH){
                RoverState = "F";
                CurrentRoverMotionState = true;
            }
            else if(Y > Sensor_MINVAL_PITCH){
                RoverState = "B";
                CurrentRoverMotionState = true;
            }
            else if(X < -Sensor_MINVAL_ROLL){
                RoverState = "R";
                CurrentRoverMotionState = true;
            }
            else if(X > Sensor_MINVAL_ROLL){
                RoverState = "L";
                CurrentRoverMotionState = true;
            }
            else{
                RoverState = "S";
                CurrentRoverMotionState = false;
            }
            //Send only when new signal occurs
            if(CurrentRoverState != RoverState){
                CurrentRoverState = RoverState;
                //Send Signal To Rover
                SendStreamMessage(CurrentRoverState);
            }
            //------------->Change Graphics
            graphicsView.setROLLPITCH(X, Y);
        }
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //Do nothing
    }
    public class GraphicsView extends View {
        Paint p;
        float ROLL, PITCH;
        float CenterX, CenterY;
        public GraphicsView(Context context) {
            super(context);
            p = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
        public void setROLLPITCH(double ROLL, double PITCH){
            this.ROLL = -(float)ROLL;
            this.PITCH = (float)PITCH;
        }
        @Override
        protected void onDraw(Canvas canvas) {
            CenterX = getWidth()/2; CenterY = getHeight() /2;
            super.onDraw(canvas);

            if(ISCONNECTEDTOSERVER) {
                p.setColor(Color.parseColor("#BBDEFB"));//Last Background
            }
            else{
                p.setColor(Color.parseColor("#FF5252"));//Last Background
            }
            canvas.drawCircle(CenterX,CenterY, getHeight()/2.2f, p);

            if(CurrentRoverMotionState) { //Motion State
                p.setColor(Color.parseColor("#AED581"));//1st Layer
            }
            else {
                p.setColor(Color.parseColor("#E57373"));//1st Layer
            }
            canvas.drawCircle(CenterX,CenterY, getHeight()/3, p);

            p.setColor(Color.parseColor("#0D47A1"));//2nt Layer
            float r = getHeight() / 10;
            float X = CenterX + (ROLL * getWidth()/10 ), Y = CenterY + (PITCH * getHeight()/10);
            canvas.drawCircle(X, Y, r, p);

            //Center Dot
            p.setColor(Color.BLACK);
            canvas.drawCircle(CenterX,CenterY, 10, p);

            invalidate();
        }
    }
}
