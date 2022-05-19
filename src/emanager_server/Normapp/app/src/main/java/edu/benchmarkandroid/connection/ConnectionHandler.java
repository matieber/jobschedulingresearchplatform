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
import com.google.gson.JsonObject;

import edu.benchmarkandroid.Benchmark.jsonConfig.BenchmarkData;
import edu.benchmarkandroid.Benchmark.jsonConfig.BenchmarksResponse;
import edu.benchmarkandroid.MainActivity;
import edu.benchmarkandroid.model.UpdateData;
import edu.benchmarkandroid.service.BenchmarkExecutor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.File;
import java.util.Date;
import java.util.concurrent.Executors;

public class ConnectionHandler extends Handler {

    private static final String TAG = "ConnectionHandler";
    private static final long RETRY_TIME_POST_RESULT = 3000;
    private static final long RETRY_TIME_REGISTER_DEVICE = 3000;
    private static final long RETRY_TIME_BATT_UPDATE = 5000;
    private static final long RETRY_TIME_GETBENCHMARKS = 10000;
    private static final long RETRY_TIME_DELETE_DEVICE = 3000;
    private static final long RETRY_TIME_SWITCH_ENERGY = 3000;

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

    private ServerListener listener;
    private String model;
    private String ipAddr;
    private int MAX_TRIES = 5;

    public void setMAX_TRIES(int MAX_TRIES) {
        this.MAX_TRIES = MAX_TRIES;
    }

    private ConnectionClient energy_connectionClient;
    private ConnectionClient device_connectionClient;

    private static Retrofit.Builder energy_builder;

    private static Retrofit energy_retrofit;

    private static Retrofit.Builder device_builder;

    private static Retrofit device_retrofit;

    private Handler runOnUiHandler;

    public static <S> S createEnergyServiceClient(Class<S> serviceClass) {
        return energy_retrofit.create(serviceClass);
    }

    public static <S> S createDeviceServiceClient(Class<S> serviceClass) {
        return device_retrofit.create(serviceClass);
    }

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
                switchEnergyState(benchExecutorMsg.getString(SWITCH_STATE), benchExecutorMsg.getString(BenchmarkExecutor.SLOTID_FIELD), benchExecutorMsg.getString(PREPARING_STAGE));
                break;
        }
    }

    public ConnectionHandler(Looper looper, ServerListener listener, String energyBaseUrl, String deviceBaseUrl, String model, String ip, int max_tries) {
        super(looper);
        this.runOnUiHandler = new Handler(((Service) listener).getApplicationContext().getMainLooper());
        this.listener = listener;
        this.model = model;
        MAX_TRIES = max_tries;
        this.ipAddr = ip;
        energy_builder =
                new Retrofit.Builder()
                        .baseUrl(energyBaseUrl)
                        .addConverterFactory(GsonConverterFactory.create(new GsonBuilder()
                                .setLenient()
                                .create()));
        device_builder =
                new Retrofit.Builder()
                        .baseUrl(deviceBaseUrl)
                        .callbackExecutor(Executors.newSingleThreadExecutor())
                        .addConverterFactory(GsonConverterFactory.create(new GsonBuilder()
                                .setLenient()
                                .create()));
        energy_retrofit = energy_builder.build();
        device_retrofit = device_builder.build();

        energy_connectionClient = ConnectionHandler.createEnergyServiceClient(ConnectionClient.class);
        device_connectionClient = ConnectionHandler.createDeviceServiceClient(ConnectionClient.class);
    }

    public void switchEnergyState(final String nextState, String slotId, final String preparingStage) {
        Log.d(TAG, "Asking motrol to switch EnergyState to: " + nextState);

        final Call<JsonObject> asyncCall = energy_connectionClient.switchState(model, nextState, slotId);

        asyncCall.enqueue(new retrofit2.Callback<JsonObject>() {
            int tries = 0;

            private void retry(long afterTime, String msg) {
                tries++;
                if (tries < MAX_TRIES) {
                    Log.d(TAG, msg + " Retrying after: " + afterTime + " milliseconds");

                    try {
                        Thread.sleep(afterTime);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    asyncCall.clone().enqueue(this);
                } else
                    listener.onFailureSwitchState(nextState, preparingStage);
            }

            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                StringBuilder logcatmsg = new StringBuilder("switchEnergyState. onServerResponse: \n");
                if (response.isSuccessful()) {
                    logcatmsg.append("response.isSuccessful(): true\n" + response.body());
                    Log.d(TAG, logcatmsg.toString());
                    listener.onSuccessSwitchState(nextState, preparingStage);
                } else {
                    logcatmsg.append("response.isSuccessful(): false\n");
                    retry(RETRY_TIME_SWITCH_ENERGY, logcatmsg.toString());
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                StringBuilder logcatmsg = new StringBuilder("switchEnergyState. onFailure: \n" + t.getMessage());
                retry(RETRY_TIME_BATT_UPDATE, logcatmsg.toString());
            }
        });

    }

    public void putUpdateBatteryState(UpdateData updateData) {

        Log.d(TAG, "putUpdateBatteryState: " + updateData.toString());

        final Call<JsonObject> asyncCall = device_connectionClient.putUpdateBatteryState(model, updateData);

        asyncCall.enqueue(new retrofit2.Callback<JsonObject>() {
            int tries = 0;

            private void retry(long afterTime, String msg) {
                tries++;
                if (tries < MAX_TRIES) {
                    Log.d(TAG, msg + " Retrying after: " + afterTime + " milliseconds");

                    try {
                        Thread.sleep(afterTime);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    asyncCall.clone().enqueue(this);
                } else
                    listener.onFailureUpdateBatteryState();
            }

            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                StringBuilder logcatmsg = new StringBuilder("putUpdateBatteryState. onResponse: \n");
                if (response.isSuccessful()) {
                    logcatmsg.append("response.isSuccessful(): true\n" + response.body());
                    Log.d(TAG, logcatmsg.toString());
                    listener.onSuccessUpdateBatteryState();
                } else {
                    logcatmsg.append("response.isSuccessful(): false\n");
                    retry(RETRY_TIME_BATT_UPDATE, logcatmsg.toString());
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                StringBuilder logcatmsg = new StringBuilder("putUpdateBatteryState. onFailure: \n" + t.getMessage());
                retry(RETRY_TIME_BATT_UPDATE, logcatmsg.toString());
            }
        });
    }

    public void getBenchmarks() {

        final Call<BenchmarksResponse> callSync = device_connectionClient.getBenchmarks(model);

        callSync.enqueue(new retrofit2.Callback<BenchmarksResponse>() {
            int tries = 0;

            private synchronized void retry(long afterTime, String msg) {
                tries++;
                if (tries < MAX_TRIES) {
                    Log.d(TAG, msg + " Retrying after: " + afterTime + " milliseconds");
                    try {
                        this.wait(afterTime);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    callSync.clone().enqueue(this);
                } else {
                    Log.d(TAG, "getBenchmarks: retrofit max retries reached!");
                    listener.onFailureGetBenchmarks();
                    //this.runOnUiThread_onFailureGetBenchmarks();
                }
            }

            private void runOnUiThread_onFailureGetBenchmarks() {
                runOnUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onFailureGetBenchmarks();
                    }
                });
            }

            @Override
            public void onResponse(Call<BenchmarksResponse> call, Response<BenchmarksResponse> response) {
                StringBuilder logcatmsg = new StringBuilder("getBenchmarks. onResponse: \n");

                if (response.isSuccessful()) {
                    logcatmsg.append("response.isSuccessful(): true.\n");
                    if (response.body().getSuccess()) {
                        final BenchmarksResponse benchmarksResponse = response.body();
                        logcatmsg.append("response.body(): " + benchmarksResponse.toString() + "\n");
                        Log.d(TAG, logcatmsg.toString());
                        //Log.d(TAG, "Successful Benchmark retried: JobId:" + benchmarksResponse.getJobId().toString() + "benchmarkData:" + benchmarksResponse.getBenchmarkData().toString());
                        listener.onSuccessGetBenchmarks(benchmarksResponse.getJobId(), benchmarksResponse.getBenchmarkData());
                        //this.runOnUiThread_onSuccessGetBenchmarks(benchmarksResponse.getJobId(), benchmarksResponse.getBenchmarkData());
                    } else {
                        logcatmsg.append("response.body(): null." + "\n");
                        listener.onSuccessGetBenchmarks(null, null);
                        //runOnUiThread_onSuccessGetBenchmarks(null, null);
                    }
                } else {
                    logcatmsg.append("response.isSuccessful(): false.\n");
                    retry(RETRY_TIME_GETBENCHMARKS, logcatmsg.toString());
                }

            }

            private void runOnUiThread_onSuccessGetBenchmarks(final String jobId, final BenchmarkData benchmarkData) {
                // runOnUiThread
                runOnUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onSuccessGetBenchmarks(jobId, benchmarkData);
                    }
                });
            }

            @Override
            public void onFailure(Call<BenchmarksResponse> call, Throwable t) {
                Log.d(TAG, "getBenchmarks", t);
                StringBuilder logcatmsg = new StringBuilder("getBenchmarks. onFailure: \n");
                logcatmsg.append(t.getMessage() + " \nRetry nmb " + tries + "\n");
                retry(RETRY_TIME_GETBENCHMARKS, logcatmsg.toString());
            }
        });
    }

    public void putRegisterDevice(UpdateData updateData) {
        Log.d(TAG, "putRegisterDevice: " + updateData.toString());
        final Call<JsonObject> callSync = device_connectionClient.putUpdateBatteryState(model, updateData);

        callSync.enqueue(new retrofit2.Callback<JsonObject>() {
            int tries = 0;

            private void retry(long afterTime, String msg) {
                tries++;
                if (tries < MAX_TRIES) {
                    Log.d(TAG, msg);
                    try {
                        Thread.sleep(afterTime);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    callSync.clone().enqueue(this);
                } else {
                    Log.d(TAG, "putRegisterDevice: max retries reached!");
                    listener.onFailureDeviceRegistration();
                }

            }

            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                Log.d(TAG, "onResponse: registerDevice");

                if (response.isSuccessful()) {
                    Log.d(TAG, "onResponse: response: \n" + response.body());
                    listener.onSuccessDeviceRegistration();
                } else {
                    retry(RETRY_TIME_REGISTER_DEVICE, "onResponse: fail to register device; try nmb=" + (tries + 1));
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                retry(RETRY_TIME_REGISTER_DEVICE, "onFailure: registerDevice. Retrying nmb " + tries);
            }
        });
    }

    public void postResult(String jobId, String localFilePath) {
        Log.d(TAG,"Sending result to server, jobId: "+ jobId);
        File file = null;
        if (localFilePath.equals(BenchmarkExecutor.EMPTY_RESULT_FILE_NAME)) {
            try {
                file = File.createTempFile("tmp", "zip", new File(MainActivity.PATH));
            } catch (Exception ex) {
                Log.d(TAG, "Can't create temp file", ex);
            }
        } else
            file = new File(localFilePath);
        RequestBody requestFile = RequestBody.create(MediaType.parse("*/*"), file);
        MultipartBody.Part body = MultipartBody.Part.createFormData("fileName", jobId, requestFile);
        final Call<JsonObject> callSync = device_connectionClient.postResult(model, jobId, body);

        callSync.enqueue(
                new retrofit2.Callback<JsonObject>() {
            int retries = 0;
            private void retry(long afterTime) {
                if (retries < MAX_TRIES) {
                    try {
                        Thread.sleep(afterTime);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    callSync.clone().enqueue(this);
                } else
                    listener.onFailurePostResult();
            }

            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "successful postJobResult. servermsg: " + response.body());
                    listener.onSuccessPostResult();
                } else {
                    Log.d(TAG, "onResponse: postResult: notSuccessful. Retrying" + response.errorBody() +"after " + RETRY_TIME_POST_RESULT + " milliseconds");
                    retry(RETRY_TIME_POST_RESULT);
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Log.d(TAG, "onFailure postResult: fail to post result. retrying after " + RETRY_TIME_POST_RESULT + " milliseconds. \n" + "throwable.getMessage(): " + t.getMessage());
                retry(RETRY_TIME_POST_RESULT);
            }
        });
    }

    public void deleteDevice(String message) {
        Log.d(TAG, "deleteDevice: " + message);

        final Call<JsonObject> asynCall = device_connectionClient.deleteDevice(model, ipAddr);

        asynCall.enqueue(new retrofit2.Callback<JsonObject>() {

            int tries = 0;

            private void retry(long afterTime, String msg) {
                tries++;
                if (tries < MAX_TRIES) {
                    Log.d(TAG, msg + " Retrying after: " + afterTime + "milliseconds");
                    try {
                        Thread.sleep(afterTime);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    asynCall.clone().enqueue(this);
                } else {
                    listener.onFailureDeleteDevice();
                }
            }

            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                StringBuilder logcatmsg = new StringBuilder("deleteDevice. onResponse: \n");

                if (response.isSuccessful()) {
                    logcatmsg.append("response.isSuccessful(): true \nresponse.body()=" + response.body());
                    Log.d(TAG, logcatmsg.toString());
                    listener.onSuccessDeleteDevice();
                } else {
                    logcatmsg.append("response.isSuccessful(): false \n");
                    retry(RETRY_TIME_DELETE_DEVICE, logcatmsg.toString());
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                StringBuilder logcatmsg = new StringBuilder("deleteDevice. onFailure: " + t.getMessage() + "\n");
                retry(RETRY_TIME_DELETE_DEVICE, logcatmsg.toString());
            }
        });
    }

}