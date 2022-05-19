package edu.benchmarkandroid.connection;

import edu.benchmarkandroid.Benchmark.jsonConfig.BenchmarkData;

public interface ServerListener {

    void onSuccessUpdateBatteryState();

    void onFailureUpdateBatteryState();

    void onSuccessGetBenchmarks(String jobId, BenchmarkData benchmarkData);

    void onFailureGetBenchmarks();

    void onSuccessPostResult();

    void onFailurePostResult();


    void onSuccessDeviceRegistration();

    void onFailureDeviceRegistration();

    void onFailureDeleteDevice();

    void onSuccessDeleteDevice();

    void onSuccessSwitchState(String nextState, String preparingStage);

    void onFailureSwitchState(String nextState, String preparingStage);

    void onUserInputRequired();
}
