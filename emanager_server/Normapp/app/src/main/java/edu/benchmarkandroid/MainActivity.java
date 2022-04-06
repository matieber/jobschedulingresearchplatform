package edu.benchmarkandroid;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import edu.benchmarkandroid.service.BenchmarkExecutor;
import edu.benchmarkandroid.utils.BatteryUtils;
import edu.benchmarkandroid.utils.LogGUI;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import static android.Manifest.permission.*;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";


    // CHANGE THIS CONSTANTS TO THE VALUE OF YOUR PREFERENCE
    public static final String PATH = "/sdcard/Download/";
    private static final int MY_PERMISSIONS_REQUEST_INTERNET = 53;
    private static final int MY_PERMISSIONS_REQUEST_EXTERNAL_STORAGE = 3;
    private static final int MY_PERMISSIONS_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = 5;

    // View components
    private EditText ipEditText;
    private EditText portEditText;
    private TextView ipTextView;
    private TextView portTextView;
    private TextView modelTextView;
    private Button startButton;
    private Button exitButton;
    private Button backToScreenOnActivity;
    private TextView stateTextView;
    private TextView logTextView;
    private ScrollView scrollView;

    private static String model;
    public static String PERFORM_ACTION = "PERFORM_ACTION";
    public static String INFORM_SERVICE_RUNNING = "RUNNING";
    public static String INFORM_SERVICE_SUSPENDED = "SUSPENDED";
    public static String INFORM_SERVICE_TERMINATED = "TERMINATED";
    public static String INFORM_SERVICE_JOB_FINISHED = "JOB_FINISHED";
    public static String INFORM_SERVICE_PROGRESS = "INFORM_SERVICE_PROGRESS";
    public static String SET_KEEP_SCREEN_ON = "SET_KEEP_SCREEN_ON";
    public static String UNSET_KEEP_SCREEN_ON = "UNSET_KEEP_SCREEN_ON";
    public static String SERVICE_MSG = "SERVICE_MSG";


    public static int MAX_BRITENESS = 255;


    private String httpAddress = "192.168.0.X";
    private String httpPort = "1080";
    private String slotId = "";
    private Intent execServiceIntent;
    private String benchmarkfile = "";
    private int screenon_activity_id = 1;
    private Intent screen_on_intent = null;

    private class ResponseHandler extends Handler {

        private void setupScreenOnGUI() {
            screen_on_intent = new Intent(getApplicationContext(), ScreenOnActivity.class);
            backToScreenOnActivity.setEnabled(true);
            openScreenOnActivity();
        }

        private void setupScreenOffGUI() {
            screen_on_intent = null;
            closeScreenOnActivity();
            backToScreenOnActivity.setEnabled(false);
            backToScreenOnActivity.setVisibility(View.INVISIBLE);
        }

        @Override
        public void handleMessage(Message message) {

            Bundle serviceMsg = message.getData();
            if (serviceMsg.getString(PERFORM_ACTION).compareTo(SET_KEEP_SCREEN_ON) == 0) {
                //setupScreenOnGUI();
            } else {
                if (serviceMsg.getString(PERFORM_ACTION).compareTo(UNSET_KEEP_SCREEN_ON) == 0) {
                    //setupScreenOffGUI();
                    //getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
                    dpm.lockNow();
                } else {
                    if (serviceMsg.getString(PERFORM_ACTION).compareTo(INFORM_SERVICE_PROGRESS) != 0) {
                        if (serviceMsg.getString(PERFORM_ACTION).compareTo(INFORM_SERVICE_SUSPENDED) == 0
                                || serviceMsg.getString(PERFORM_ACTION).compareTo(INFORM_SERVICE_TERMINATED) == 0) {
                            stopService();
                            startButton.setEnabled(true);
                        }
                        LogGUI.log(serviceMsg.getString(SERVICE_MSG));
                    }
                    stateTextView.setText(serviceMsg.getString(SERVICE_MSG));
                }
            }
        }
    }

    private void closeScreenOnActivity() {
        finishActivity(screenon_activity_id);
    }

    private void openScreenOnActivity() {
        if (screen_on_intent != null)
            startActivityForResult(screen_on_intent, screenon_activity_id);
    }

    Messenger messenger;


    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        model = Build.MANUFACTURER + "_" + Build.MODEL;
        model = model.replace(" ", "_")
                .replace(")", "")
                .replace("(", "")
                .replace("-", "_");


        ipEditText = findViewById(R.id.IpText);
        ipTextView = findViewById(R.id.ipTextView);
        portTextView = findViewById(R.id.portTextView);
        portEditText = findViewById(R.id.portText);

        modelTextView = findViewById(R.id.modelTextView);
        stateTextView = findViewById(R.id.stateTextView);
        logTextView = findViewById(R.id.logTextView);
        scrollView = findViewById(R.id.scrollView);

        LogGUI.init(logTextView, scrollView);
        ipEditText.setText(httpAddress);
        portEditText.setText(httpPort);

        startButton = findViewById(R.id.startButton);
        exitButton = findViewById(R.id.endButton);
        backToScreenOnActivity = findViewById(R.id.returnToScreenOnPane);

        ipTextView.setText(httpAddress);
        portTextView.setText(httpPort);
        modelTextView.setText(model);
        stateTextView.setText("Build.VERSION.SDK_INT: " + Build.VERSION.SDK_INT);

        //set onChangeListener to display the complete formater url to the user
        bindInputToDisplayText(ipEditText, ipTextView);
        bindInputToDisplayText(portEditText, portTextView);
        scrollView.requestFocus();
        messenger = new Messenger(new ResponseHandler());
        exitButton.setEnabled(true);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startButton.setEnabled(false);
                ipTextView.setEnabled(false);
                portTextView.setEnabled(false);
                initiateBenchmarksExecutionSet();
            }
        });

        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService();
                finishAndRemoveTask();
            }
        });

        backToScreenOnActivity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openScreenOnActivity();
            }
        });

        checkPermissions();
        loadServerConfigProperties();
        printDeviceInformation();

    }

    private void printDeviceInformation() {
        LogGUI.clearAndLog("Device model: " + model);
        LogGUI.log("Build.VERSION.SDK_INT: " + Build.VERSION.SDK_INT);
        LogGUI.log("Working dir: " + PATH);
        LogGUI.log("Device IP address: " + getIpAddress());
        LogGUI.log("Device Motrol SlotId: " + slotId);
        LogGUI.log("BatteryCapacity: " + BatteryUtils.getBatteryCapacity(this.getApplicationContext()));
        LogGUI.log("Critical device battery level configured: " + BenchmarkExecutor.CRITICAL_BATTERY_LEVEL * 100 + "%");
    }

    private void initiateBenchmarksExecutionSet() {

        execServiceIntent = new Intent(this, BenchmarkExecutor.class);
        execServiceIntent.putExtra(BenchmarkExecutor.DEVICE_MODEL_FIELD, MainActivity.model);
        execServiceIntent.putExtra(BenchmarkExecutor.PORT_FIELD, String.valueOf(portTextView.getText()));
        execServiceIntent.putExtra(BenchmarkExecutor.IPADDRESS_FIELD, String.valueOf(ipTextView.getText()));
        execServiceIntent.putExtra(BenchmarkExecutor.DEVICE_IP_ADDRESS, getIpAddress());
        String energyServiceUrl = String.format("http://%s:%s/energy/", String.valueOf(ipTextView.getText()), String.valueOf(portTextView.getText()));
        String deviceServiceUrl = String.format("http://%s:%s/device/", String.valueOf(ipTextView.getText()), String.valueOf(portTextView.getText()));
        execServiceIntent.putExtra(BenchmarkExecutor.ENERGY_SERVER_FIELD, energyServiceUrl);
        execServiceIntent.putExtra(BenchmarkExecutor.DEVICE_SERVER_FIELD, deviceServiceUrl);
        execServiceIntent.putExtra(BenchmarkExecutor.SLOTID_FIELD, slotId);
        execServiceIntent.putExtra(BenchmarkExecutor.BENCHMARKSFILE_FIELD, benchmarkfile);
        execServiceIntent.putExtra(BenchmarkExecutor.MESSENGER_FIELD, messenger);
        ContextCompat.startForegroundService(this, execServiceIntent);

    }

    private String getIpAddress() {
        WifiManager wifiMan = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInf = wifiMan.getConnectionInfo();
        int ipAddress = wifiInf.getIpAddress();
        String ip = String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
        return ip;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_INTERNET: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Internet Permission Granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "We need this permission", Toast.LENGTH_SHORT).show();
                    ActivityCompat.requestPermissions(this,
                            new String[]{INTERNET},
                            MY_PERMISSIONS_REQUEST_INTERNET);
                }
            }

            case MY_PERMISSIONS_REQUEST_EXTERNAL_STORAGE:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "STORAGE Permission Granted", Toast.LENGTH_SHORT).show();
                    loadServerConfigProperties();
                    printDeviceInformation();
                    ComponentName mDeviceAdminSample = new ComponentName(this, AdminReceiver.class);
                    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mDeviceAdminSample);
                    this.startActivityForResult(intent, 0);

                } else {
                    Toast.makeText(this, "We need this permission", Toast.LENGTH_SHORT).show();
                    ActivityCompat.requestPermissions(this,
                            new String[]{READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE},
                            MY_PERMISSIONS_REQUEST_EXTERNAL_STORAGE);
                }
        }
    }


    private void bindInputToDisplayText(final EditText input, final TextView display) {
        input.addTextChangedListener(
                new TextWatcher() {

                    public void afterTextChanged(Editable s) {
                        display.setText(input.getText());
                    }

                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }
                }
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (screen_on_intent != null) {
            backToScreenOnActivity.setVisibility(View.VISIBLE);
        }

    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (screen_on_intent != null) {
            backToScreenOnActivity.setVisibility(View.VISIBLE);
        }
    }

    //unregister the battery monitor
    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.stopService();
        Log.d(TAG, "OnDestroy() activity call: stopping benchmarkExecutor foreground service");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop() invoked");
    }

    public void stopService() {
        Intent serviceIntent = new Intent(this, BenchmarkExecutor.class);
        stopService(serviceIntent);
    }

    // Private init methods ------------------------------------------------------------------------

    private void loadServerConfigProperties() {
        try {
            Properties serverConfigProperties = new Properties();
            serverConfigProperties.load(new FileInputStream(PATH + "serverConfig.properties"));
            this.httpAddress = serverConfigProperties.getProperty("httpAddress");
            this.httpPort = serverConfigProperties.getProperty("httpPort");
            this.slotId = serverConfigProperties.getProperty("slotId");
            this.benchmarkfile = serverConfigProperties.getProperty("benchmarksFile", "");
            ipEditText.setText(httpAddress);
            portEditText.setText(httpPort);
        } catch (IOException e) {
            stateTextView.setText("Can't find " + PATH + "serverConfig.properties (app isn't started at the server side?)");
            LogGUI.log("onCreate: Can't find" + PATH + "serverConfig.properties");
            Log.e(TAG, "onCreate: Can't find " + PATH + "serverConfig.properties", e);
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_EXTERNAL_STORAGE);
        }

        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), INTERNET)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.INTERNET},
                    MY_PERMISSIONS_REQUEST_INTERNET);
        }

        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS},
                    MY_PERMISSIONS_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        }

    }


}
