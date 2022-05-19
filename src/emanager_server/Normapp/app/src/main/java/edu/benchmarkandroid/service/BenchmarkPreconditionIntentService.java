package edu.benchmarkandroid.service;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;
import androidx.annotation.Nullable;
import edu.benchmarkandroid.Benchmark.BatteryStopCondition;
import edu.benchmarkandroid.Benchmark.StopCondition;
import edu.benchmarkandroid.Benchmark.benchmarks.cpuBenchmark.CPUUserThread;

public class BenchmarkPreconditionIntentService extends IntentService {

    public static final String END_DISCHARGE_ACTION = "endDischarge";
    public static final String END_CHARGE_ACTION = "endCharge";
    private static final String TAG = "BenchmarkPreconditionIntentService";
    // 5-second wait
    private static final int WAIT_TIME = 5000;

    CPUUserThread[] cpuUsers = null;
    int cpusToUse = 0;

    public BenchmarkPreconditionIntentService() {
        super("BenchmarkPreconditionIntentService");
    }

    private void configureCpuNumber(float cpuPct) {
        int cpus = 0;
        if (cpuPct > 0.0) {
            int availableCpus = Runtime.getRuntime().availableProcessors();
            while (cpus < availableCpus) {
                cpus++;
                float ratio = (float) cpus / (float) availableCpus;
                if (ratio >= cpuPct)
                    break;
            }
        }
        this.cpusToUse = cpus;
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {

        double targetLevel = Double.parseDouble(intent.getStringExtra("targetLevel"));
        double currentLevel = BatteryNotificator.getInstance().getCurrentLevel();

        if (currentLevel < targetLevel) {
            waitCharge(targetLevel);
        } else if (currentLevel > targetLevel && targetLevel != -1) {
            float cpuPct = intent.getFloatExtra("cpuPct", 1.0f);
            waitDischarge(targetLevel, cpuPct);
        } else {
            broadCastFinishEvent(END_CHARGE_ACTION);
        }
    }

    private synchronized void waitCharge(double targetLevel) {
        BatteryNotificator battNotificator = BatteryNotificator.getInstance();
        while (battNotificator.getCurrentLevel() < targetLevel) {
            try {
                this.wait(WAIT_TIME);
                //Thread.sleep(WAIT_TIME);
            } catch (InterruptedException ex) {
                Log.d(TAG, "this.wait(WAIT_TIME) in BenchmarkPreconditionIntentService.waitCharge() interrupted!");
            }
        }
        // Finished! Let BenchmarkExecutor know
        broadCastFinishEvent(END_CHARGE_ACTION);
    }

    private void broadCastFinishEvent(String action) {
        Intent responseIntent = new Intent();
        responseIntent.setAction(action);
        this.sendBroadcast(responseIntent);
    }

    private synchronized void waitDischarge(double targetLevel, float cpuPct) {
        StopCondition condition = new BatteryStopCondition(targetLevel, BatteryNotificator.getInstance());

        if (condition.canContinue()) {
            configureCpuNumber(cpuPct);
            Log.d(TAG, "Starting " + cpusToUse + " threads for acceleration.");

            cpuUsers = new CPUUserThread[cpusToUse];
            for (int i = 0; i < cpusToUse; i++) {
                cpuUsers[i] = new CPUUserThread();
                cpuUsers[i].setSleep(0);
                cpuUsers[i].start();
            }

            while (condition.canContinue()) {
                try {
                    this.wait(WAIT_TIME);
                } catch (InterruptedException ex) {
                    Log.d(TAG, "this.wait(WAIT_TIME) in DischargeAcceleratorIntentService.waitDischarge() interrupted!");
                }
            }
            this.destroySpawnedThreads();
        }
        // Finished! Let BenchmarkExecutor know
        broadCastFinishEvent(END_DISCHARGE_ACTION);
    }

    private void destroySpawnedThreads() {
        if (cpuUsers == null) return;
        for (int i = 0; i < cpusToUse; i++) {
            cpuUsers[i].kill();
            try {
                cpuUsers[i].join();
            } catch (InterruptedException ex) {
                Log.d(TAG, "Interrupted while joining thread: " + cpuUsers[i].toString());
            }
            cpuUsers[i] = null;
        }
        cpuUsers = null;
    }

    @Override
    public void onDestroy() {
        this.destroySpawnedThreads();
        super.onDestroy();
    }
}

