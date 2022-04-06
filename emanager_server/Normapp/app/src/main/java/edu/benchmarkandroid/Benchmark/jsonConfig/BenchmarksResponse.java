package edu.benchmarkandroid.Benchmark.jsonConfig;

import android.os.Parcel;
import android.os.Parcelable;

public class BenchmarksResponse implements Parcelable {

    Boolean success;
    BenchmarkData benchmarkData;
    String jobId;

    public BenchmarksResponse() {
    }

    protected BenchmarksResponse(Parcel in) {
        byte tmpSuccess = in.readByte();
        success = tmpSuccess == 0 ? null : tmpSuccess == 1;
        byte tmpJobId = in.readByte();
        jobId = tmpJobId == 0 ? null : in.readString();
        benchmarkData = in.readParcelable(BenchmarkData.class.getClassLoader());
    }

    public static final Creator<BenchmarksResponse> CREATOR = new Creator<BenchmarksResponse>() {
        @Override
        public BenchmarksResponse createFromParcel(Parcel in) {
            return new BenchmarksResponse(in);
        }

        @Override
        public BenchmarksResponse[] newArray(int size) {
            return new BenchmarksResponse[size];
        }
    };

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public BenchmarkData getBenchmarkData() {
        return benchmarkData;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getJobId() {
        return jobId;
    }

    public void setBenchmarkData(BenchmarkData benchmarkData) {
        this.benchmarkData = benchmarkData;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (success == null ? 0 : success ? 1 : 2));
        if (jobId == null)
            dest.writeByte((byte) 0);
        else {
            dest.writeByte((byte) 1);
            dest.writeString(jobId);
        }
        dest.writeParcelable(benchmarkData, flags);
    }

    @Override
    public String toString() {
        return "BenchmarksResponse{" +
                "message=" + success +
                ", jobId=" + jobId +
                '}';
    }
}
