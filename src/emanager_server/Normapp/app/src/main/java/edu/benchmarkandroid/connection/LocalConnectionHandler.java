package edu.benchmarkandroid.connection;

import android.app.Service;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import edu.benchmarkandroid.Benchmark.jsonConfig.BenchmarkData;
import edu.benchmarkandroid.Benchmark.jsonConfig.BenchmarkDefinition;
import edu.benchmarkandroid.Benchmark.jsonConfig.Variant;
import edu.benchmarkandroid.model.UpdateData;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

/**
 * This is an implementation of a local connection handler that respond to messages of the protocol
 * followed by the BenchmarkExecutor by simulating the existence of a local dewsim_server running
 * at the movile device. It only implements of user inputs waits
 **/
public class LocalConnectionHandler extends Handler {

    private static final String TAG = "LocalConnectionHandler";

    public static final int REQUEST_PUT_BATTERY_STATE_UPDATE = 50;
    public static final int REQUEST_PUT_REGISTER_DEVICE = 51;
    public static final int REQUEST_GET_BENCHMARKS = 52;
    public static final int REQUEST_POST_BENCHMARKRESULT = 54;
    public static final int REQUEST_DELETE_DEVICE = 55;
    public static final int REQUEST_SWITCH_ENERGY = 56;

    public static final String REQUEST = "request";
    public static final String UPDATE_DATA = "updateData";
    public static final String RES_FILENAME = "resFileName";
    public static final String RES_FILEPATH = "resFilePath";
    public static final String DEL_MSG = "deletionMsg";
    public static final String SWITCH_STATE = "switchState";
    public static final String PREPARING_STAGE = "preparingStage";
    private final Gson jsonBuilder;

    private ServerListener listener;
    private Handler runOnUiHandler;
    private boolean benchDownloaded = false;
    private String benchmarksfile;


    @Override
    public void handleMessage(@NonNull Message msg) {
        super.handleMessage(msg);
        Bundle benchExecutorMsg = msg.getData();

        switch (benchExecutorMsg.getInt(REQUEST)) {
            case REQUEST_PUT_BATTERY_STATE_UPDATE:
                putUpdateBatteryState((UpdateData) benchExecutorMsg.get(UPDATE_DATA));
                break;
            case REQUEST_PUT_REGISTER_DEVICE:
                putRegisterDevice((UpdateData) benchExecutorMsg.get(UPDATE_DATA));
                break;
            case REQUEST_GET_BENCHMARKS:
                getBenchmarks();
                break;
            case REQUEST_POST_BENCHMARKRESULT:
                postResult(benchExecutorMsg.getString(RES_FILENAME), benchExecutorMsg.getString(RES_FILEPATH));
                break;
            case REQUEST_DELETE_DEVICE:
                deleteDevice(benchExecutorMsg.getString(DEL_MSG));
                break;
            case REQUEST_SWITCH_ENERGY:
                switchEnergyState(benchExecutorMsg.getString(SWITCH_STATE), benchExecutorMsg.getString(PREPARING_STAGE));
                break;
        }
    }

    public LocalConnectionHandler(Looper looper, ServerListener listener, String benchmarkfile) {
        super(looper);
        this.runOnUiHandler = new Handler(((Service) listener).getApplicationContext().getMainLooper());
        this.benchmarksfile = benchmarkfile;
        this.listener = listener;
        this.jsonBuilder = new GsonBuilder().setLenient().create();
    }

    public void switchEnergyState(final String nextState, final String preparingStage) {
        Log.d(TAG, "Battery State: " + nextState + " Required Battery State: " + preparingStage);
        runOnUiHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onUserInputRequired();
                listener.onSuccessSwitchState(nextState, preparingStage);
            }
        });
    }


    /**
     * Ignore battery update
     */
    public void putUpdateBatteryState(UpdateData updateData) {
        return;
    }

    public void getBenchmarks() {
        if (!benchDownloaded) {
            StringBuilder logcatmsg = new StringBuilder("getBenchmarks invoked using " + this.benchmarksfile + "\n");
            try {
                FileReader benchReader = new FileReader(new File(this.benchmarksfile));
                final BenchmarkData benchmarks = jsonBuilder.fromJson(jsonBuilder.newJsonReader(benchReader), BenchmarkData.class);
                logcatmsg.append("benchmarks file content: " + benchmarks.toString() + "\n");
                Log.d(TAG, logcatmsg.toString());
                benchDownloaded = true;
                runOnUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        String jobId = null;
                        String variantId = benchmarks.getRunOrder().get(0);
                        for (BenchmarkDefinition bd : benchmarks.getBenchmarkDefinitions()) {
                            for (Variant v : bd.getVariants())
                                if (v.getVariantId().compareTo(variantId) == 0) {
                                    jobId = bd.getBenchmarkId();
                                    break;
                                }
                            if (jobId != null) break;
                        }
                        listener.onSuccessGetBenchmarks(jobId, benchmarks);
                    }
                });

            } catch (FileNotFoundException e) {
                Log.d(TAG, "Benchmarks file not found: " + this.benchmarksfile);
                runOnUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onFailureGetBenchmarks();
                    }
                });
                e.printStackTrace();
            }
        } else {
            runOnUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onSuccessGetBenchmarks(null, null);
                }
            });

        }
    }

    /*Device Registration is always success**/
    public void putRegisterDevice(UpdateData updateData) {
        Log.d(TAG, "putRegisterDevice: " + updateData.toString());
        runOnUiHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onSuccessDeviceRegistration();
            }
        });
    }

    /*Result post is always success**/
    public void postResult(String jobId, String localFilePath) {
        runOnUiHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onSuccessPostResult();
            }
        });

    }

    /*Device deletion is always success**/
    public void deleteDevice(String message) {
        Log.d(TAG, "deleteDevice: " + message);
        listener.onSuccessDeleteDevice();
    }

}