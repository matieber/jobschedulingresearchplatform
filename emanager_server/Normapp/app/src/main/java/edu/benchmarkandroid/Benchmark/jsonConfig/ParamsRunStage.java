package edu.benchmarkandroid.Benchmark.jsonConfig;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class ParamsRunStage implements Parcelable {

    @SerializedName("screenState")
    @Expose
    private String screenState = "on";

    @SerializedName("names")
    @Expose
    private List<String> names = new ArrayList<>();

    @SerializedName("values")
    @Expose
    private List<String> values = new ArrayList<String>();

    protected ParamsRunStage(Parcel in) {
        in.readStringList(names);
        in.readStringList(values);
        Log.d("ParamsRunStage", names.toString());
        Log.d("ParamsRunStage", values.toString());
    }

    public static final Creator<ParamsRunStage> CREATOR = new Creator<ParamsRunStage>() {
        @Override
        public ParamsRunStage createFromParcel(Parcel in) {
            return new ParamsRunStage(in);
        }

        @Override
        public ParamsRunStage[] newArray(int size) {
            return new ParamsRunStage[size];
        }
    };

    protected int getIndexOf(String paramName) {
        int index = 0;
        for (String par : names) {
            if (paramName.equals(par))
                break;
            index = index + 1;
        }
        return index;
    }

    public String getValue(String paramName) {
        return values.get(getIndexOf(paramName));
    }

    public Double getCpuLevel() {
        return Double.parseDouble(getValue("cpuLevel"));
    }

    public String getScreenState() {
        return screenState;
    }

    public void setScreenState(String screenState) {
        this.screenState = screenState;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(screenState);
        dest.writeStringList(names);
        dest.writeStringList(values);
    }

    @Override
    public String toString() {
        return "ParamsRunStage";
    }

    public Integer getIntValue(String paramName) {
        return Integer.parseInt(this.getValue(paramName));
    }

    public Double getDoubleValue(String paramName) {
        return Double.parseDouble(this.getValue(paramName));
    }

    public Boolean getBooleanValue(String paramName) {
        return Boolean.parseBoolean(this.getValue(paramName));
    }

}

