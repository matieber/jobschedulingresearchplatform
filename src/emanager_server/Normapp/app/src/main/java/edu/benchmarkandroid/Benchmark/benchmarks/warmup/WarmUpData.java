package edu.benchmarkandroid.Benchmark.benchmarks.warmup;

import java.io.Serializable;

public class WarmUpData implements Serializable {
    private int cpus;
    private boolean isGPUAvailable;
    private float averageCPUTime;
    private float averageCPUInferring;
    private float averageGPUInferring;

    public int getCpus() {
        return cpus;
    }

    public void setCpus(int cpus) {
        this.cpus = cpus;
    }

    public boolean isGPUAvailable() {
        return isGPUAvailable;
    }

    public void setGPUAvailable(boolean GPUAvailable) {
        isGPUAvailable = GPUAvailable;
    }

    public float getAverageCPUTime() {
        return averageCPUTime;
    }

    public void setAverageCPUTime(float averageCPUTime) {
        this.averageCPUTime = averageCPUTime;
    }

    public float getAverageCPUInferring() {
        return averageCPUInferring;
    }

    public void setAverageCPUInferring(float averageCPUInferring) {
        this.averageCPUInferring = averageCPUInferring;
    }

    public float getAverageGPUInferring() {
        return averageGPUInferring;
    }

    public void setAverageGPUInferring(float averageGPUInferring) {
        this.averageGPUInferring = averageGPUInferring;
    }

    @Override
    public String toString() {
        return "WarmUpData{" +
                "cpus=" + cpus +
                ", isGPUAvailable=" + isGPUAvailable +
                ", averageCPUTime=" + averageCPUTime +
                ", averageCPUInferring=" + averageCPUInferring +
                ", averageGPUInferring=" + averageGPUInferring +
                '}';
    }
}
