package edu.benchmarkandroid.Benchmark;


import android.content.Context;
import android.content.res.AssetManager;
import edu.benchmarkandroid.Benchmark.jsonConfig.Variant;
import edu.benchmarkandroid.service.ProgressUpdater;

public abstract class Benchmark {
    private Variant variant;
    private AssetManager am;
    private Context context;

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    private String serverIp;

    public Benchmark(Variant variant) {
        this.variant = variant;
    }

    public Variant getVariant() {
        return variant;
    }

    public void setVariant(Variant variant) {
        this.variant = variant;
    }

    public String getId() {
        return variant.getVariantId();
    }

    public void setAssets(AssetManager am) {
        this.am = am;
    }

    public AssetManager getAssets() {
        return am;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    public abstract void runSampling(StopCondition stopCondition, ProgressUpdater progressUpdater);

    public abstract void runBenchmark(StopCondition stopCondition, ProgressUpdater progressUpdater);

    public abstract void gentleTermination();

}
