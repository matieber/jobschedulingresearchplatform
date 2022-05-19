package edu.benchmarkandroid.Benchmark.jsonConfig;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class BenchmarkData implements Parcelable {

    @SerializedName("benchmarkDefinitions")
    @Expose
    private List<BenchmarkDefinition> benchmarkDefinitions = null;

    @SerializedName("runOrder")
    @Expose
    private List<String> runOrder = null;

    protected BenchmarkData(Parcel in) {
        runOrder = in.createStringArrayList();
    }

    public static final Creator<BenchmarkData> CREATOR = new Creator<BenchmarkData>() {
        @Override
        public BenchmarkData createFromParcel(Parcel in) {
            return new BenchmarkData(in);
        }

        @Override
        public BenchmarkData[] newArray(int size) {
            return new BenchmarkData[size];
        }
    };

    public List<BenchmarkDefinition> getBenchmarkDefinitions() {
        return benchmarkDefinitions;
    }

    public void setBenchmarkDefinitions(List<BenchmarkDefinition> benchmarkDefinitions) {
        this.benchmarkDefinitions = benchmarkDefinitions;
    }

    public List<String> getRunOrder() {
        return runOrder;
    }

    public void setRunOrder(List<String> runOrder) {
        this.runOrder = runOrder;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringList(runOrder);
    }

    @Override
    public String toString() {
        return "BenchmarkData{" +
                "benchmarkDefinitions=" + benchmarkDefinitions +
                ", runOrder=" + runOrder +
                '}';
    }
}
