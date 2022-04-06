package edu.benchmarkandroid.Benchmark.jsonConfig;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class BenchmarkDefinition implements Parcelable {

    public static final String BENCHMARKS_OUTER_PACKAGE = "edu.benchmarkandroid.Benchmark.benchmarks.";

    @SerializedName("benchmarkId")
    @Expose
    private String benchmarkId;
    @SerializedName("benchmarkClass")
    @Expose
    private String benchmarkClass;
    @SerializedName("variants")
    @Expose
    private List<Variant> variants = null;

    protected BenchmarkDefinition(Parcel in) {
        benchmarkId = in.readString();
        benchmarkClass = in.readString();

        String benchFullClassName = BENCHMARKS_OUTER_PACKAGE + benchmarkClass;
        try {
            Class.forName(benchFullClassName);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static final Creator<BenchmarkDefinition> CREATOR = new Creator<BenchmarkDefinition>() {
        @Override
        public BenchmarkDefinition createFromParcel(Parcel in) {
            return new BenchmarkDefinition(in);
        }

        @Override
        public BenchmarkDefinition[] newArray(int size) {
            return new BenchmarkDefinition[size];
        }
    };

    public String getBenchmarkId() {
        return benchmarkId;
    }

    public void setBenchmarkId(String benchmarkId) {
        this.benchmarkId = benchmarkId;
    }

    public String getBenchmarkClass() {
        return benchmarkClass;
    }

    public void setBenchmarkClass(String benchmarkClass) {
        this.benchmarkClass = benchmarkClass;
    }

    public List<Variant> getVariants() {
        return variants;
    }

    public void setVariants(List<Variant> variants) {
        this.variants = variants;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(benchmarkId);
        dest.writeString(benchmarkClass);
    }

    @Override
    public String toString() {
        return "BenchmarkDefinition{" +
                "benchmarkId='" + benchmarkId + '\'' +
                ", benchmarkClass='" + benchmarkClass + '\'' +
                ", variants=" + variants +
                '}';
    }
}