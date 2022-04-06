package edu.benchmarkandroid.Benchmark;

import edu.benchmarkandroid.service.ThresholdNotificator;

public class ConvergenceStopCondition implements StopCondition {
    private double convergenceThreshold;
    private ThresholdNotificator thresholdNotificator;


    public ConvergenceStopCondition(double convergenceThreshold, ThresholdNotificator thresholdNotificator) {
        this.convergenceThreshold = convergenceThreshold;
        this.thresholdNotificator = thresholdNotificator;
    }

    @Override
    public boolean canContinue() {
        double level = thresholdNotificator.getCurrentLevel();
        return convergenceThreshold < level || level < -(convergenceThreshold);
    }


}
