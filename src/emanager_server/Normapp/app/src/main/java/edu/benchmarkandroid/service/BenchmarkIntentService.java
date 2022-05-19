package edu.benchmarkandroid.service;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import edu.benchmarkandroid.Benchmark.BatteryStopCondition;
import edu.benchmarkandroid.Benchmark.Benchmark;
import edu.benchmarkandroid.Benchmark.StopCondition;
import edu.benchmarkandroid.Benchmark.jsonConfig.Variant;
import edu.benchmarkandroid.utils.Logger;

import java.io.FileNotFoundException;

public class BenchmarkIntentService extends IntentService {

    public static final String PROGRESS_BENCHMARK_ACTION = "progressBenchmark";
    public static final String END_BENCHMARK_ACTION = "endBenchmark";
    private static final String TAG = "BenchmarkIntentService";

    private Benchmark benchmark;
    private String serverip;

    public BenchmarkIntentService() {
        super("BenchmarkIntentService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();
        Benchmark benchmark = null;
        try {
            Class<Benchmark> benchmarkClass = (Class<Benchmark>) Class.forName(intent.getStringExtra("className"));
            benchmark = benchmarkClass.getConstructor(Variant.class).newInstance(gson.fromJson(intent.getStringExtra("benchmarkVariant"), Variant.class));
            this.serverip = intent.getStringExtra("serverIp");
            this.benchmark = benchmark;
            benchmark.setAssets(this.getAssets());
            benchmark.setContext(this.getApplicationContext());
            benchmark.setServerIp(this.serverip);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Logger.init("run-" + benchmark.getVariant().getVariantId() + ".txt");
        Logger logger = null;
        try {
            logger = Logger.getInstance();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        ProgressUpdater progressUpdater = new BenchmarkProgressUpdater(
                this,
                PROGRESS_BENCHMARK_ACTION,
                END_BENCHMARK_ACTION,
                benchmark.getVariant().getVariantId(),
                logger);
        Log.d(TAG, "Initiating Benchmark");
        Log.d(TAG, printBenchmarkInfo());

        StopCondition condition = new BatteryStopCondition(
                benchmark.getVariant().getEnergyPreconditionRunStage().getEndBatteryLevel(),
                BatteryNotificator.getInstance());
        benchmark.runSampling(condition, progressUpdater);
        benchmark.runBenchmark(condition, progressUpdater);

    }

    /**
     * private String printBenchmarkInfo(){
     * return "VariantId: " + benchmark.getVariant().getVariantId() +
     * ", EnergyState: "+ benchmark.getVariant().getEnergyPreconditionRunStage().toString()+
     * ", ScreenState: " + benchmark.getVariant().getParamsRunStage().getScreenState() +
     * ", TargetCPU: " + benchmark.getVariant().getParamsRunStage().getCpuLevel();
     * }
     */

    private String printBenchmarkInfo() {
        return "VariantId: " + benchmark.getVariant().getVariantId() +
                ", EnergyState: " + benchmark.getVariant().getEnergyPreconditionRunStage().toString();
    }

    @Override
    public void onDestroy() {
        if (benchmark != null)
            benchmark.gentleTermination();

        super.onDestroy();
    }
}
