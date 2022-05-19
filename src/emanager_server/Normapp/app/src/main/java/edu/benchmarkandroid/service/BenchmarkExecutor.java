package edu.benchmarkandroid.service;

import android.app.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.*;
import android.util.Log;
import android.view.Display;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.google.gson.GsonBuilder;
import edu.benchmarkandroid.Benchmark.jsonConfig.BenchmarkData;
import edu.benchmarkandroid.Benchmark.jsonConfig.BenchmarkDefinition;
import edu.benchmarkandroid.Benchmark.jsonConfig.Variant;
import edu.benchmarkandroid.MainActivity;
import edu.benchmarkandroid.connection.ConnectionHandler;
import edu.benchmarkandroid.connection.LocalConnectionHandler;
import edu.benchmarkandroid.connection.ServerListener;
import edu.benchmarkandroid.model.UpdateData;
import edu.benchmarkandroid.utils.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static android.os.PowerManager.*;
import static edu.benchmarkandroid.service.BenchmarkIntentService.END_BENCHMARK_ACTION;
import static edu.benchmarkandroid.service.BenchmarkIntentService.PROGRESS_BENCHMARK_ACTION;
import static edu.benchmarkandroid.service.BenchmarkPreconditionIntentService.END_CHARGE_ACTION;
import static edu.benchmarkandroid.service.BenchmarkPreconditionIntentService.END_DISCHARGE_ACTION;

public class BenchmarkExecutor extends Service implements ServerListener {

    public static final String EMPTY_RESULT_FILE_NAME = "empty.zip";
    public static final String IPADDRESS_FIELD = "ip";
    public static final String PORT_FIELD = "port";
    public static final String ENERGY_SERVER_FIELD = "energyBaseUrl";
    public static final String DEVICE_SERVER_FIELD = "deviceBaseUrl";
    public static final String SLOTID_FIELD = "slotId";
    public static final String DEVICE_MODEL_FIELD = "model";
    public static final String DEVICE_IP_ADDRESS = "ipaddr";
    public static final String MESSENGER_FIELD = "messenger";
    public static final String BENCHMARKSFILE_FIELD = "benchmarksfile";

    public static final int BATTERY_UPDATE_INTERVAL = 5000;
    public static final double CRITICAL_BATTERY_LEVEL = 0.01;
    private static BenchmarkExecutor benchmarkExecutorInstance = null;

    private static int restartNmb = 0;
    private boolean currentBenchWaitMsgPrinted = false;
    //private Boolean plugged = null;
    private String powerStatus = "unknown";
    private static final String TAG = "BenchmarkExecutor";
    private static String CHANNEL_ID = "appChannel";
    private BatteryNotificator batteryNotificator;
    private WifiNotificator wifiNotificator;

    private Context context;

    private Handler connectionHandler;
    private static Messenger uiHandler;
    private PowerManager pm;
    private DisplayManager dm;
    private WifiManager cm;
    //private BatteryManager              bm;

    private HashMap<String, Variant> variantMap;
    private HashMap<String, String> variantClassNames;

    private String serverip;
    private String currentJobId;
    private int currentBenchmark;
    private Variant currentVariant;

    private boolean benchReqScreenOn = true;
    private String reqBattState;
    private double reqStartBattLevel = 0d;
    private double reqEndBattLevel;
    private String reqNextSwitchState = null;
    private List<String> benchExecOrder = null;

    // Receiver for updates from the benchmark run
    private BatteryInfoReceiver batteryInfoReceiver;
    private ScreenInfoReceiver screenInfoReceiver;
    private PreconditionReceiver preconditionReceiver;
    private WifiInfoReceiver wifiInfoReceiver;

    private Intent actualServiceIntent = null;
    private Intent actualPreconditionIntent = null;

    private long timeOfLastBatteryUpdate = System.currentTimeMillis();

    private int deviceCpuMhz;
    private int deviceBatteryMah;

    public static PowerManager.WakeLock LOCK_REAL;
    public static PowerManager.WakeLock LOCK_SCREEN;
    private int slotId;
    //mutex
    private Boolean running = false;
    private Boolean benchmarkRequestInProgress = false;
    private Boolean deviceRegistered = false;
    protected Boolean levelPreconditionMet = false;

    // Suscriber to battery notifications from the OS
    private ProgressReceiver progressReceiver;
    private HandlerThread ht = null;


    private void sendMessageToUI(String action, String message) {
        Message m = Message.obtain();
        Bundle b = new Bundle();
        b.putString(MainActivity.PERFORM_ACTION, action);
        b.putString(MainActivity.SERVICE_MSG, message);
        m.setData(b);
        //use the handler to send message
        try {
            uiHandler.send(m);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {

        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "BenchmarkExecutorService";
            String description = "";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void initService(Intent intent) {
        this.uiHandler = (Messenger) intent.getParcelableExtra(MESSENGER_FIELD);

        Log.d(TAG, "Benchmark Executor Service restartNmb:" + BenchmarkExecutor.restartNmb);
        if (BenchmarkExecutor.restartNmb > 0) {
            Log.d(TAG, "Benchmark Executor Service initialization aborted. There is another service instance running. restartNmb:" + BenchmarkExecutor.restartNmb);
            sendMessageToUI(MainActivity.INFORM_SERVICE_TERMINATED, "Benchmark Executor Service initialization aborted. Check for other running instance");
            throw new RuntimeException("The service crashed because an attempt to launch more than one BenchmarkExecutor Service");
        }
        BenchmarkExecutor.restartNmb++;
        this.context = getApplicationContext();
        deviceRegistered = false;
        timeOfLastBatteryUpdate = System.currentTimeMillis();

        deviceBatteryMah = BatteryUtils.getBatteryCapacity(this);
        deviceCpuMhz = CPUUtils.getMaxCPUFreqMHz();
        this.wifiNotificator = WifiNotificator.getInstance();
        this.batteryNotificator = BatteryNotificator.getInstance();

        dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        pm = (PowerManager) (this.context.getSystemService(POWER_SERVICE));
        cm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);


        LOCK_REAL = pm.newWakeLock(PARTIAL_WAKE_LOCK, "Service:Lock");
        LOCK_SCREEN = pm.newWakeLock(SCREEN_DIM_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP, "Service:Screen");
        LOCK_REAL.acquire();

        batteryInfoReceiver = new BatteryInfoReceiver();
        screenInfoReceiver = new ScreenInfoReceiver();
        wifiInfoReceiver = new WifiInfoReceiver();
        ScreenStateNotificator.getInstance().setIsScreenOn(ScreenStateUtils.isScreenOn(context));

        IntentFilter filterProgressReceiver = new IntentFilter();
        filterProgressReceiver.addAction(PROGRESS_BENCHMARK_ACTION);
        filterProgressReceiver.addAction(END_BENCHMARK_ACTION);
        this.progressReceiver = new ProgressReceiver();
        this.context.registerReceiver(progressReceiver, filterProgressReceiver);

        IntentFilter filterPreconditionReceiver = new IntentFilter();
        filterPreconditionReceiver.addAction(BenchmarkPreconditionIntentService.END_CHARGE_ACTION);
        filterPreconditionReceiver.addAction(BenchmarkPreconditionIntentService.END_DISCHARGE_ACTION);
        preconditionReceiver = new PreconditionReceiver();
        this.context.registerReceiver(preconditionReceiver, filterPreconditionReceiver);

        this.context.registerReceiver(batteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        this.context.registerReceiver(batteryInfoReceiver, new IntentFilter(Intent.ACTION_POWER_CONNECTED));
        this.context.registerReceiver(batteryInfoReceiver, new IntentFilter(Intent.ACTION_POWER_DISCONNECTED));
        this.context.registerReceiver(screenInfoReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
        this.context.registerReceiver(screenInfoReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        this.context.registerReceiver(wifiInfoReceiver, new IntentFilter(WifiManager.RSSI_CHANGED_ACTION));

        this.batteryNotificator.updateBatteryLevel(BatteryUtils.getBatteryLevel(context));


        this.slotId = Integer.parseInt(intent.getStringExtra(SLOTID_FIELD));
        this.ht = new HandlerThread("ConnectionHandlerThread");
        ht.start();
        this.serverip = intent.getStringExtra(IPADDRESS_FIELD);
        batteryNotificator.updateDevicePlug(BatteryUtils.isPlugged(context));
        if (this.serverip.toLowerCase().contains("localhost")) {
            Log.d(TAG, "Executing in standalone mode");
            connectionHandler = new LocalConnectionHandler(ht.getLooper(), this, MainActivity.PATH + intent.getStringExtra(BENCHMARKSFILE_FIELD));
        } else {
            connectionHandler = new ConnectionHandler(ht.getLooper(), this, intent.getStringExtra(ENERGY_SERVER_FIELD), intent.getStringExtra(DEVICE_SERVER_FIELD), intent.getStringExtra(DEVICE_MODEL_FIELD), intent.getStringExtra(DEVICE_IP_ADDRESS), 5);
            this.sendPutRegisterDeviceMessage(new UpdateData(deviceCpuMhz, deviceBatteryMah, wifiNotificator.getCurrentSignalStrength(), BatteryUtils.getBatteryLevel(context), slotId));
        }
        BenchmarkExecutor.benchmarkExecutorInstance = this;
    }

    public static Context getBenchmarkExecutorAppContext(){
        if ( benchmarkExecutorInstance != null ){
            return benchmarkExecutorInstance.getApplicationContext();
        }
        return null;
    }

    @Override
    public boolean stopService(Intent intent) {
        Log.d(TAG, "serviceStop(Intent intent) invoked!");
        this.stopBenchmark();
        if (ht != null) {
            ht.quitSafely();
            ht = null;
        }
        stopForeground(true);
        return true;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroying service invoked!");
        super.onDestroy();
        this.stopService(null);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        initService(intent);
        this.createNotificationChannel();
        Notification not = new NotificationCompat.Builder(this, CHANNEL_ID).
                setContentText("BenchmarkExecutorService").
                setContentInfo("").build();
        startForeground(1, not);

        return START_NOT_STICKY;
    }

    private void turnScreenOn() {
        Log.d(TAG, "Before TurningScreenOn - isDisplayOn: " + ScreenStateNotificator.getInstance().isScreenOn());
        if (!LOCK_SCREEN.isHeld())
            LOCK_SCREEN.acquire();

        sendMessageToUI(MainActivity.SET_KEEP_SCREEN_ON, "");
    }

    private void turnScreenOff() {
        Log.d(TAG, "Before TurningScreenOff - isDisplayOn: " + ScreenStateNotificator.getInstance().isScreenOn());

        if (LOCK_SCREEN.isHeld())
            LOCK_SCREEN.release();

        if (!LOCK_REAL.isHeld())
            LOCK_REAL.acquire();

        sendMessageToUI(MainActivity.UNSET_KEEP_SCREEN_ON, "");
    }

    public boolean isBenchReqScreenOn() {
        return benchReqScreenOn;
    }

    private void setBenchmarkData(final String jobId, final BenchmarkData benchmarkData) {

        final List<BenchmarkDefinition> definitions = benchmarkData.getBenchmarkDefinitions();
        String benchmarkName = null;
        String benchClassName = null;
        List<Variant> allVariantsDefinition = new ArrayList<Variant>();
        benchExecOrder = benchmarkData.getRunOrder();

        variantMap = new HashMap<String, Variant>();
        variantClassNames = new HashMap<String, String>();
        currentJobId = jobId;

        for (BenchmarkDefinition definition : definitions) {
            benchmarkName = definition.getBenchmarkClass();
            benchClassName = "edu.benchmarkandroid.Benchmark.benchmarks." + benchmarkName;
            for (Variant v : definition.getVariants()) {
                if (benchExecOrder.contains(v.getVariantId())) {
                    variantMap.put(v.getVariantId(), v);
                    variantClassNames.put(v.getVariantId(), benchClassName);
                    allVariantsDefinition.add(v);
                }
            }
        }

        Log.d(TAG, "jobId: " + jobId + ", variants: " + allVariantsDefinition.toString());
        currentBenchmark = 0;

        this.progressReceiver.setVariantCount(variantMap.keySet().size());
        this.progressReceiver.setCurrentJobId(currentJobId);
    }

    private Variant loadNextVariantData() {

        currentVariant = currentBenchmark < benchExecOrder.size() ? variantMap.get(benchExecOrder.get(currentBenchmark)) : null;
        if (currentVariant == null) return null;

        this.reqStartBattLevel = currentVariant.getEnergyPreconditionRunStage().getStartBatteryLevel();
        this.reqBattState = currentVariant.getEnergyPreconditionRunStage().getRequiredBatteryState();
        this.reqEndBattLevel = currentVariant.getEnergyPreconditionRunStage().getEndBatteryLevel();
        actualServiceIntent = new Intent(context, BenchmarkIntentService.class);

        synchronized (levelPreconditionMet) {
            double current = BatteryNotificator.getInstance().getCurrentLevel();
            // -1 indicates reqStartBatteryLevel is any
            levelPreconditionMet = (this.reqStartBattLevel == -1.0f) || (this.reqStartBattLevel == current);
            if (!levelPreconditionMet && (this.reqStartBattLevel > current)) {
                this.reqNextSwitchState = "charging_ac";
            } else if (!levelPreconditionMet && (this.reqStartBattLevel < current)) {
                this.reqNextSwitchState = "discharging";
            }
        }

        actualPreconditionIntent = new Intent(context, BenchmarkPreconditionIntentService.class);
        actualPreconditionIntent.putExtra("targetLevel", String.valueOf(this.reqEndBattLevel));
        //actualPreconditionIntent.putExtra("cpuPct", 1.0f);

        String screenState = currentVariant.getParamsRunStage().getScreenState();
        if (screenState.equalsIgnoreCase("on"))
            benchReqScreenOn = true;
        else if (screenState.equalsIgnoreCase("off"))
            benchReqScreenOn = false;

        currentBenchmark++;
        return currentVariant;
    }

    public void notifyPreconditionLevelMet() {
        synchronized (this.levelPreconditionMet) {
            this.levelPreconditionMet = true;
        }
    }

    private boolean checkPreconditions() {

        //LogGUI.log("level reached?: " + levelPreconditionMet + ", requiredEnergyState: " + currentVariant.getEnergyPreconditionRunStage().getRequiredBatteryState() + ", plugged: " + batteryNotificator.isDevicePlugged() + ", reqStartBattLevel: " + this.reqStartBattLevel + ", reqNextSwitchState: " + this.reqNextSwitchState);
        synchronized (levelPreconditionMet) {
            if (!levelPreconditionMet) {
                boolean serviceRunning = false;
                ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                //next line uncommented for Debugging
                //sendMessageToUI(MainActivity.INFORM_SERVICE_PROGRESS, manager.getRunningServices(Integer.MAX_VALUE).toString());
                for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                    if (BenchmarkPreconditionIntentService.class.getName().equals(service.service.getClassName())) {
                        serviceRunning = true;
                        LogGUI.log("Charge/discharge service running...!");
                        break;
                    }
                }
                // Is the charging/discharging service running?
                if (!serviceRunning) {
                    sendSwitchEnergyMessage(this.reqNextSwitchState, "reachEnergyLevel",0);
                    startService(actualPreconditionIntent);
                    return false;
                }
            }
        }
        boolean plugged = batteryNotificator.isDevicePlugged();
        if ((plugged && (this.reqBattState.equals("charging_ac") || this.reqBattState.equals("charging_usb")))
            || !plugged && this.reqBattState.equals("discharging"))
            return true;

        sendSwitchEnergyMessage(this.reqBattState, "reachEnergyState",0);

        return false;
    }

    private synchronized void runBenchmark() {

        if (isBenchReqScreenOn())
            turnScreenOn();
        else
            if(!isBenchReqScreenOn())
                turnScreenOff();

        //LogGUI.log("[" + new Date(System.currentTimeMillis()).toString() + "] Executing " + currentVariant.getVariantId());

        actualServiceIntent.putExtra("className", variantClassNames.get(currentVariant.getVariantId()));
        actualServiceIntent.putExtra("benchmarkVariant", new GsonBuilder().create().toJson(currentVariant));
        actualServiceIntent.putExtra("serverIp", serverip);
        running = true;
        startService(actualServiceIntent);

    }

    public void stopBenchmark() {
        Log.d(TAG, "stopBenchmark invoked");
        this.context.unregisterReceiver(this.batteryInfoReceiver);
        this.context.unregisterReceiver(this.progressReceiver);
        this.context.unregisterReceiver(this.screenInfoReceiver);
        this.context.unregisterReceiver(this.preconditionReceiver);
        this.context.unregisterReceiver(this.wifiInfoReceiver);
        if (LOCK_SCREEN.isHeld())
            LOCK_SCREEN.release();
        sendMessageToUI(MainActivity.PERFORM_ACTION, MainActivity.UNSET_KEEP_SCREEN_ON);

        if (LOCK_REAL.isHeld())
            LOCK_REAL.release();

        if (actualServiceIntent != null) {
            context.stopService(actualServiceIntent);
            actualServiceIntent = null;
        }

        if (actualPreconditionIntent != null) {
            context.stopService(actualPreconditionIntent);
            actualPreconditionIntent = null;
        }
    }

    private synchronized void requestToStartBenchmarks() {
        if (loadNextVariantData() != null) {

            if (!checkPreconditions()) {
                currentBenchmark--;
                return;
            }
            //running = true;
            String batteryStatus = batteryNotificator.isDevicePlugged() ? "charging" : "discharging";
            Log.d(TAG, "Starting variantId=" + currentVariant.getVariantId() + ", reqBattState=" + reqBattState + ", reqStartBattLevel=" + reqStartBattLevel + ", reqEndBattLevel=" + reqEndBattLevel + ", currBattLevel=" + batteryNotificator.getCurrentLevel() + ", currBattState=" + batteryStatus);
            // When the device is unplugged, battery updates are much less frequent due to power saving policies of the OS. Then,
            // force a battery update to speed up the next benchmark initiation
            if (!batteryNotificator.isDevicePlugged() && ((System.currentTimeMillis() - timeOfLastBatteryUpdate) > BATTERY_UPDATE_INTERVAL)) {
                timeOfLastBatteryUpdate = System.currentTimeMillis();
                sendPutUpdateBatteryStateMessage(new UpdateData(deviceCpuMhz, deviceBatteryMah, wifiNotificator.getCurrentSignalStrength(), batteryNotificator.getCurrentLevel(), slotId));
            }
            runBenchmark();
        } else {
            Log.d(TAG, "There are no more benchmarks to run");
            sendMessageToUI(MainActivity.INFORM_SERVICE_JOB_FINISHED, "There are no more benchmarks to run");
            running = false;
            benchmarkRequestInProgress = false;
            downloadNextBenchmarks(0);
        }
    }

    private void sendPutUpdateBatteryStateMessage(UpdateData nextBenchData) {
        Message m = Message.obtain();
        Bundle b = new Bundle();
        b.putInt(ConnectionHandler.REQUEST, ConnectionHandler.REQUEST_PUT_BATTERY_STATE_UPDATE);
        b.putParcelable(ConnectionHandler.UPDATE_DATA, nextBenchData);
        m.setData(b);
        connectionHandler.sendMessage(m);
    }

    private void sendGetBenchmarksMessage(long delayMillis) {
        sendMessageToUI(MainActivity.INFORM_SERVICE_PROGRESS, "Trying to get benchmarks...");
        Log.d(TAG,"Trying to get benchmarks...");
        Message m = Message.obtain();
        Bundle b = new Bundle();
        b.putInt(ConnectionHandler.REQUEST, ConnectionHandler.REQUEST_GET_BENCHMARKS);
        m.setData(b);
        connectionHandler.sendMessageDelayed(m, delayMillis);
    }

    private void sendDeleteDeviceMesssage(String msg) {
        Message m = Message.obtain();
        Bundle b = new Bundle();
        b.putInt(ConnectionHandler.REQUEST, ConnectionHandler.REQUEST_DELETE_DEVICE);
        b.putString(ConnectionHandler.DEL_MSG, msg);
        m.setData(b);
        connectionHandler.sendMessage(m);
    }

    private void sendPutRegisterDeviceMessage(UpdateData data) {
        Message m = Message.obtain();
        Bundle b = new Bundle();
        b.putInt(ConnectionHandler.REQUEST, ConnectionHandler.REQUEST_PUT_REGISTER_DEVICE);
        b.putParcelable(ConnectionHandler.UPDATE_DATA, data);
        m.setData(b);
        connectionHandler.sendMessage(m);
    }

    private void sendPostResultMessage(String jobId, String filePath) {
        Log.d(TAG, "Start sending job result to server: " + jobId);
        Message m = Message.obtain();
        Bundle b = new Bundle();
        b.putInt(ConnectionHandler.REQUEST, ConnectionHandler.REQUEST_POST_BENCHMARKRESULT);
        b.putString(ConnectionHandler.RES_FILENAME, jobId);
        b.putString(ConnectionHandler.RES_FILEPATH, filePath);
        m.setData(b);
        connectionHandler.sendMessage(m);
    }

    private void sendSwitchEnergyMessage(String nextState, String preparingStage, long delayMilis) {
        Message m = Message.obtain();
        Bundle b = new Bundle();
        b.putInt(ConnectionHandler.REQUEST, ConnectionHandler.REQUEST_SWITCH_ENERGY);
        Log.d(TAG, "Signal sent to motrol to put slot id in " + nextState);
        //LogGUI.log("nextState: " + nextState + ", preparingStage: " + preparingStage);
        b.putString(ConnectionHandler.SWITCH_STATE, nextState);
        b.putString(SLOTID_FIELD, String.valueOf(this.slotId));
        b.putString(ConnectionHandler.PREPARING_STAGE, preparingStage);
        m.setData(b);
        connectionHandler.sendMessageDelayed(m,delayMilis);
    }

    @Override
    public void onSuccessUpdateBatteryState() {
    }

    @Override
    public void onFailureUpdateBatteryState() {
        Log.d(TAG, "Battery update failure!");
    }

    @Override
    public void onSuccessDeviceRegistration() {
        setDeviceRegistered();
    }

    private synchronized void setDeviceRegistered() {
        if (!deviceRegistered) {
            sendMessageToUI(MainActivity.INFORM_SERVICE_RUNNING, "[" + new Date(System.currentTimeMillis()).toString() + "] Device Registration Succeed");
            this.deviceRegistered = true;
            downloadNextBenchmarks(0);
        }
    }

    private synchronized void downloadNextBenchmarks(final long delayMillis) {
        // TODO: Use other options to keep the interface responsive
            if (!benchmarkRequestInProgress) {
                benchmarkRequestInProgress = true;
                sendGetBenchmarksMessage(delayMillis);
            }
    }

    @Override
    public void onFailureDeviceRegistration() {
        sendMessageToUI(MainActivity.INFORM_SERVICE_RUNNING, "[" + new Date(System.currentTimeMillis()).toString() + "] Retrying Device Registration");
    }

    @Override
    public void onFailureDeleteDevice() {
        sendMessageToUI(MainActivity.INFORM_SERVICE_RUNNING, "[" + new Date(System.currentTimeMillis()).toString() + "] Retrying device deletion");
    }

    @Override
    public void onSuccessDeleteDevice() {
        sendMessageToUI(MainActivity.INFORM_SERVICE_RUNNING, "[" + new Date(System.currentTimeMillis()).toString() + "] Aborting service execution");
        stopService(null);
    }

    @Override
    public void onSuccessSwitchState(String nextState, String preparingStage) {
        //avoid processing multiple successSwithState that could be caused by retrofit multiple request
        processOnSuccessSwitchState(nextState, preparingStage);
    }

    private synchronized void processOnSuccessSwitchState(String nextState, String preparingStage){
        if (running) return;
        // We are successfully switching energy, considering that energy level has been reached
        // If the switch request was for energy level, we should not call requestToStartBenchmarks()
        if (preparingStage.compareTo("reachEnergyState") == 0)
            Log.d(TAG, "Energy switched successfully to: " + nextState);
        this.requestToStartBenchmarks();
    }

    @Override
    public void onFailureSwitchState(String nextState, String preparingStage) {
        Log.d(TAG, "Can't switch energy to: " + nextState);
        if (running)
            throw new RuntimeException("Shouldn't switch while running!");
        sendSwitchEnergyMessage(nextState, preparingStage, 1000);
    }

    @Override
    public void onSuccessGetBenchmarks(String jobId, BenchmarkData benchmarkData) {
        if (benchmarkData != null) {
            //sendMessageToUI(MainActivity.INFORM_SERVICE_RUNNING, "[" + new Date(System.currentTimeMillis()).toString() + "] Benchmarks received, jobId: " + jobId);
            sendMessageToUI(MainActivity.INFORM_SERVICE_PROGRESS,  "Benchmarks received, jobId: " + jobId);
            setBenchmarkData(jobId, benchmarkData);
            requestToStartBenchmarks();
        } else {
            Log.d(TAG,"Calling onFailureGetBenchmark because Benchmark JobId retrieved: "+ jobId + " BenchmarkData: null");
            onFailureGetBenchmarks();
        }
    }

    @Override
    public synchronized void onFailureGetBenchmarks() {
        sendMessageToUI(MainActivity.INFORM_SERVICE_PROGRESS, "[" + new Date(System.currentTimeMillis()).toString() + "] Retrying benchmarks retrieval");
        Log.d(TAG, "Retrying benchmarks retrieval");
        this.benchmarkRequestInProgress = false;
        downloadNextBenchmarks(2000);
    }

    @Override
    public synchronized void onSuccessPostResult() {
        Log.d(TAG, "[" + new Date(System.currentTimeMillis()).toString() + "] Benchmark result successfully received by the server");
        this.benchmarkRequestInProgress = false;
        downloadNextBenchmarks(0);
    }

    @Override
    public void onFailurePostResult() {
        Log.d(TAG, "Retrying benchmark post result");
        sendMessageToUI(MainActivity.INFORM_SERVICE_RUNNING, "[" + new Date(System.currentTimeMillis()).toString() + "] Retrying benchmark post result");
    }

    @Override
    public void onUserInputRequired() {
        Uri alarmSound =
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        MediaPlayer mp = MediaPlayer.create(getApplicationContext(), alarmSound);
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer mp) {
                mp.release();
            }
        });
        mp.start();
    }


    public class BatteryInfoReceiver extends BroadcastReceiver {

        private static final String CHARGING = "Charging";
        private static final String DISCHARGING = "Discharging";

        private void handleOnBatteryLevelChanged(Intent intent) {

            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            batteryNotificator.updateBatteryLevel((level / (double) scale));

            /*
             * @TODO El móvil no debiera retirarse del server, sino terminar la tarea actual y
             *   marcarla como no completada para que el server sepa.
             */
            if (batteryNotificator.getCurrentLevel() <= CRITICAL_BATTERY_LEVEL) {
                String msg = "BenchmarkExecution service crashed due to device reached critical low battery=" + batteryNotificator.getCurrentLevel() + " (eq or less than " + CRITICAL_BATTERY_LEVEL + "%)";
                sendDeleteDeviceMesssage(msg);
                throw new RuntimeException(msg);
            }
            String status = null;
            if (batteryNotificator.isDevicePlugged()) //assumes a charging/discharging based on the power cable state
                status = CHARGING;
            else
                status = DISCHARGING;

            /**The following code was commented because the behavior corresponds to the benchmarking platform*/
            /*if (running) {
                StringBuffer st = new StringBuffer();
                st.append(System.currentTimeMillis());
                st.append(',');
                st.append(status);
                st.append(',');
                st.append(intent.getExtras().get(BatteryManager.EXTRA_LEVEL));
                Log.d(TAG,status+", currBatteryLevel: "+batteryNotificator.getCurrentLevel());
                try {
                    Logger.getInstance().write(st.toString());
                } catch (FileNotFoundException e) {
                    Log.d(TAG, "battery: Logger not found - fname: " + Logger.fname);
                }
            }
            else{ // sent battery updates only in case the device is not executing a benchmark
            */
            UpdateData data = new UpdateData(deviceCpuMhz, deviceBatteryMah, wifiNotificator.getCurrentSignalStrength(), batteryNotificator.getCurrentLevel(), slotId);
            if (deviceRegistered) {
                if (! batteryNotificator.hasCurrentLevelBeenReported() ||
                        (System.currentTimeMillis() - timeOfLastBatteryUpdate) > BATTERY_UPDATE_INTERVAL) {
                    batteryNotificator.updateLastReportedLevel(batteryNotificator.getCurrentLevel());
                    timeOfLastBatteryUpdate = System.currentTimeMillis();
                    sendPutUpdateBatteryStateMessage(data);
                    Log.d(TAG, "BatteryUpdate: reqStartBattLevel=" + reqStartBattLevel + ", currBattLevel=" + batteryNotificator.getCurrentLevel() + ", lastReportedBattLevel=" + batteryNotificator.getLastReportedLevel() + " battState=" + status + " plugged=" + batteryNotificator.isDevicePlugged());
                }
            } else {
                sendPutRegisterDeviceMessage(data);
            }
            //}

        }

        private void handleOnPowerStateChangeEvent(Intent intent) {
            //providing that connecting and disconnecting power cord causes the device screen to turn on,
            //this method assures that screen turns off immediately if the benchmark requires such state.
            if (!isBenchReqScreenOn())//means that benchmark requires screen off
                turnScreenOff();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            batteryNotificator.updateDevicePlug(BatteryUtils.isPlugged(context));
            handleOnPowerStateChangeEvent(intent);
            handleOnBatteryLevelChanged(intent);
        }
    }

    public class ProgressReceiver extends BroadcastReceiver {

        private String currentJobId = null;
        private int variantCount = 0;
        private int endActionsSoFar = 0;
        private Vector<String> fileNames = new Vector<String>();

        public void setCurrentJobId(String currentJobId) {
            this.currentJobId = currentJobId;
        }

        public void setVariantCount(int variantCount) {
            this.variantCount = variantCount;
        }

        private void processEndAction(Intent intent) {
            running = false;
            this.endActionsSoFar++;
            String fname = intent.getStringExtra("file");
            byte[] result = null;
            StringBuilder msg = new StringBuilder("[" + new Date(System.currentTimeMillis()).toString() + "] ");
            try {
                File file = new File(fname);
                FileInputStream fileInputStream = new FileInputStream(file);
                result = new byte[(int) file.length()];
                fileInputStream.read(result);
                this.fileNames.add(fname);
                msg.append(fname + " finished!");
            } catch (IOException e) {
                msg.append("IOException while saving result of " + intent.getStringExtra("variant") + " " + e.getMessage());

            }
            Log.d(TAG, msg.toString());
            sendMessageToUI(MainActivity.INFORM_SERVICE_RUNNING, msg.toString());

            if (this.endActionsSoFar == variantCount) {
                String zipFileName = zipFiles();
                sendPostResultMessage(currentJobId, zipFileName);
                reset();
            } else {
                requestToStartBenchmarks();
            }
        }

        private void reset() {
            variantCount = 0;
            endActionsSoFar = 0;
            fileNames.clear();
            currentJobId = null;
        }

        private String zipFiles() {
            try {
                String zipFileName = MainActivity.PATH + "/" + "results-" + currentJobId + ".zip";
                FileOutputStream fos = new FileOutputStream(zipFileName);
                ZipOutputStream zipOut = new ZipOutputStream(fos);
                for (String srcFile : fileNames) {
                    File fileToZip = new File(srcFile);
                    FileInputStream fis = new FileInputStream(fileToZip);
                    ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
                    zipOut.putNextEntry(zipEntry);
                    byte[] bytes = new byte[1024];
                    int length;
                    while ((length = fis.read(bytes)) >= 0) {
                        zipOut.write(bytes, 0, length);
                    }
                    fis.close();
                }
                zipOut.close();
                fos.close();
                return zipFileName;
            } catch (IOException ex) {
                Log.d(TAG, "Can't zip files", ex);
                return EMPTY_RESULT_FILE_NAME;
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            //benchmark run stage report
            if (intent.getAction().equals(PROGRESS_BENCHMARK_ACTION))
                sendMessageToUI(MainActivity.INFORM_SERVICE_PROGRESS, intent.getStringExtra("msg"));
            else if (intent.getAction().equals(END_BENCHMARK_ACTION))
                processEndAction(intent);
        }
    }

    private class PreconditionReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(END_CHARGE_ACTION)) {
                sendMessageToUI(MainActivity.INFORM_SERVICE_PROGRESS, "Required charge finished.");
                Log.d(TAG, "Required charge finished");
            }
            if (intent.getAction().equals(END_DISCHARGE_ACTION)) {
                sendMessageToUI(MainActivity.INFORM_SERVICE_PROGRESS, "Required discharge finished.");
                Log.d(TAG, "Required discharge finished");
            }
            notifyPreconditionLevelMet();
            requestToStartBenchmarks();
        }
    }

    private class ScreenInfoReceiver extends BroadcastReceiver {

        private static final String SCREEN_ON = "screen_on";
        private static final String SCREEN_OFF = "screen_off";

        @Override
        public void onReceive(Context context, Intent intent) {
            String screen_status = null;
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                screen_status = SCREEN_ON;
                ScreenStateNotificator.getInstance().setIsScreenOn(true);
            }
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                screen_status = SCREEN_OFF;
                ScreenStateNotificator.getInstance().setIsScreenOn(false);
            }

            if (running) {
                StringBuffer st = new StringBuffer();
                st.append(System.currentTimeMillis());
                st.append(',');
                st.append(screen_status);
                Log.d(TAG, "Screen State changed. Now " + screen_status);
                /**The following code is commented because it make sense for the benchmarking platform*/
                /*try {
                    Logger.getInstance().write(st.toString());
                    sendMessageToUI(MainActivity.INFORM_SERVICE_PROGRESS,"Now "+screen_status);
                } catch (FileNotFoundException e) {
                    Log.d(TAG, "Screen: Logger not found - fname: " + Logger.fname);
                }*/

            }
        }

    }

    private class WifiInfoReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equals(WifiManager.RSSI_CHANGED_ACTION)) {
                int newRSSI = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, -1);
                WifiNotificator.getInstance().updateSignalStrength(newRSSI);
                sendMessageToUI(MainActivity.INFORM_SERVICE_PROGRESS, "RSSI: " + Integer.toString(newRSSI));
            }


        }
    }

}
