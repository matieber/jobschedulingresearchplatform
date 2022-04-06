package edu.benchmarkandroid.Benchmark.jsonConfig;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class ParamsSamplingStage implements Parcelable {

    @SerializedName("names")
    @Expose
    private List<String> names = new ArrayList<>();

    @SerializedName("values")
    @Expose
    private List<String> values = new ArrayList<String>();

    protected ParamsSamplingStage(Parcel in) {
        in.readStringList(names);
        in.readStringList(values);
        Log.d("ParamsSamplingStage", names.toString());
        Log.d("ParamsSamplingStage", values.toString());
    }

    public static final Creator<ParamsSamplingStage> CREATOR = new Creator<ParamsSamplingStage>() {
        @Override
        public ParamsSamplingStage createFromParcel(Parcel in) {
            return new ParamsSamplingStage(in);
        }

        @Override
        public ParamsSamplingStage[] newArray(int size) {
            return new ParamsSamplingStage[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringList(names);
        dest.writeStringList(values);
    }

    @Override
    public String toString() {
        return "ParamsSamplingStage";
    }

    protected int getIndexOf(String paramName) {
        int index = 0;
        for (String par : names) {
            if (paramName.equals(par))
                break;
            index = index + 1;
        }
        return index;
    }

    protected String getValue(String paramName) {
        return values.get(getIndexOf(paramName));
    }

    public Double getDoubleValue(String paramName) {
        return Double.parseDouble(getValue(paramName));
    }

}
