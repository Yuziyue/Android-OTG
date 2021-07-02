package com.example.myesp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private Button sendButton;
    private Button connectButton;
    private Button closeButton;
    private Button clearButton;
    private TextView consoleView;
    private ProgressBar progressBar;
    private EditText editText;
    private RadioGroup radioGroup;
    private RadioButton ascii_btn;
    private RadioButton hex_btn;

    private int FLAG = 0;       //Judge Hex or ASCII

    UsbSerialPort port;

    short b[] = new short[40000];

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private SerialInputOutputManager mSerialIoManager;

    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,

            Manifest.permission.WRITE_EXTERNAL_STORAGE};
    //请求状态码
    private static int REQUEST_PERMISSION_CODE = 1;

    private final  SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {
                @Override
                public void onRunError(Exception e) {
                    Log.d("TAG", "Runner stopped.");
                }
                @Override
                public void onNewData(final byte[] data) {
                    //TODO 新的数据
                    if(FLAG == 0){
                        runOnUiThread(()->{
                            //ASCII
                            output(new String(data));
                        });
                    }else{
                        runOnUiThread(()->{
                            //Hex
                            output((toHexString1(data)));
                        });
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Request Permissions
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_PERMISSION_CODE);
            }
        }

        sendButton = findViewById(R.id.sendButton);
        connectButton = findViewById(R.id.connectButton);
        closeButton = findViewById(R.id.closeButton);
        clearButton = findViewById(R.id.clearButton);
        consoleView = findViewById(R.id.consoleText);
        progressBar = findViewById(R.id.progressBar);
        editText = findViewById(R.id.send_Text);
        radioGroup = findViewById(R.id.radioGroup1);
        ascii_btn = findViewById(R.id.ascii_radio);
        hex_btn = findViewById(R.id.hex_radio);

        progressBar.setVisibility(View.INVISIBLE);

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> getOutputMode(checkedId));

        //Connect Port
        connectButton.setOnClickListener(v->{
            // Find all available drivers from attached devices.
            UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
            if (availableDrivers.isEmpty()) {
                return;
            }

            // Open a connection to the first available driver.
            UsbSerialDriver driver = availableDrivers.get(0);
            UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
            if (connection == null) {
                // add UsbManager.requestPermission(driver.getDevice(), ..) handling here
                //这里提前在res/xml/device_filter.xml中配置 需提前知道硬件设备的vid和pid
                Toast.makeText(this, "Permission Error", Toast.LENGTH_SHORT).show();
                return;
            }
            port = driver.getPorts().get(0); // Most devices have just one port (port 0)
            try {
                port.open(connection);
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                //Setting
                port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(port.isOpen()){
                progressBar.setVisibility(View.VISIBLE);
                output("Set port Success!");
                mSerialIoManager = new SerialInputOutputManager(port, mListener);//添加监听
                mSerialIoManager.start();
            }
        });
        //Send message
        sendButton.setOnClickListener(v->{
            String strSend = editText.getText().toString()+'\n';
            try {
                port.write(strSend.getBytes(),1000);
                Toast.makeText(this, "Write Successful", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        //Close port
        closeButton.setOnClickListener(v->{
            try {
                port.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(!port.isOpen()){
                output("Close Port!");
                progressBar.setVisibility(View.INVISIBLE);
            }
        });
        //Clear output
        clearButton.setOnClickListener(v->{
            consoleView.setText("");
        });
    }

    public void output(String content) {
        consoleView.append("> " + content + "\n");
    }

    private void getOutputMode(int buttonId){
        switch (buttonId){
            case R.id.ascii_radio:
                if(ascii_btn.isChecked()){
                    FLAG = 0;
                    //Toast.makeText(this, "ASCII格式！", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.hex_radio:
                if(hex_btn.isChecked()){
                    FLAG = 1;
                    //Toast.makeText(this, "HEX格式！", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }
    //转化为Hex格式输出
    public static String toHexString1(byte[] b){
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < b.length; ++i){
            buffer.append(toHexString1(b[i]));
        }
        return buffer.toString();
    }
    public static String toHexString1(byte b){
        String s = Integer.toHexString(b & 0xFF);
        if (s.length() == 1){
            return "0" + s;
        }else{
            return s;
        }
    }
    //转化成12bits
    public void GetDec(byte[] a){
        b = new short[40000];
        int i = 0,k=0;
        int n = a.length;
        while (i < n-1)
        {
            b[k++] = (short)(((a[i] << 4)&0x00000ff0) | ((a[i + 1] >> 4)&0x0000000f));
            i++;
            b[k++] = (short) (((a[i] << 8)&0x00000f00 )| ((a[i + 1])&0x000000ff ));
            i += 2;
        }
    }

}

