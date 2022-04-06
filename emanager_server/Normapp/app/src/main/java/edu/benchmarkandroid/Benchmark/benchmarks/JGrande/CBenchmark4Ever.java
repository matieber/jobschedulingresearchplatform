package edu.benchmarkandroid.Benchmark.benchmarks.JGrande;


import edu.benchmarkandroid.Benchmark.Benchmark;
import edu.benchmarkandroid.Benchmark.StopCondition;
import edu.benchmarkandroid.Benchmark.benchmarks.JGrande.experiments.mobility.nonmobile.DHPC_AllSizeA;
import edu.benchmarkandroid.Benchmark.jsonConfig.Variant;
import edu.benchmarkandroid.service.ProgressUpdater;

public class CBenchmark4Ever extends Benchmark {

    private ProgressUpdater progressUpdater;

    public CBenchmark4Ever(Variant variant) {
        super(variant);
    }

    @Override
    public void runSampling(StopCondition stopCondition, ProgressUpdater progressUpdater) {
    }

    @Override
    public void runBenchmark(StopCondition stopCondition, ProgressUpdater progressUpdater) {

        this.progressUpdater = progressUpdater;
        int runs = getVariant().getParamsRunStage().getIntValue("runs");
        progressUpdater.update("Using runs equals to: " + String.valueOf(runs));
        DHPC_AllSizeA jgrandeBenchmark = new DHPC_AllSizeA();

        while (stopCondition.canContinue())
            jgrandeBenchmark.run(progressUpdater, getVariant().getParamsRunStage(), stopCondition);

        progressUpdater.update("Total executed runs: " + String.valueOf(jgrandeBenchmark.getExecutedCount()));
        progressUpdater.end();
        this.progressUpdater = null;
    }

    @Override
    public void gentleTermination() {

        if (progressUpdater != null)
            progressUpdater.end();
    }

}