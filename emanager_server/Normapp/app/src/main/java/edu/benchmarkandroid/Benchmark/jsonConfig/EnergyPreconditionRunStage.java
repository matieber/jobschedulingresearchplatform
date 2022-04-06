package edu.benchmarkandroid.Benchmark.jsonConfig;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class EnergyPreconditionRunStage implements Parcelable {

    @SerializedName("requiredBatteryState")
    @Expose
    private String requiredBatteryState = "DISCHARGING";

    @SerializedName("startBatteryLevel")
    @Expose
    private Double startBatteryLevel = 1d;

    @SerializedName("endBatteryLevel")
    @Expose
    private Double endBatteryLevel = 0.01;


    protected EnergyPreconditionRunStage(Parcel in) {
        requiredBatteryState = in.readString();
        if (in.readByte() == 0) {
            startBatteryLevel = null;
        } else {
            startBatteryLevel = in.readDouble();
        }
        if (in.readByte() == 0) {
            endBatteryLevel = null;
        } else {
            endBatteryLevel = in.readDouble();
        }
    }

    public static final Creator<EnergyPreconditionRunStage> CREATOR = new Creator<EnergyPreconditionRunStage>() {
        @Override
        public EnergyPreconditionRunStage createFromParcel(Parcel in) {
            return new EnergyPreconditionRunStage(in);
        }

        @Override
        public EnergyPreconditionRunStage[] newArray(int size) {
            return new EnergyPreconditionRunStage[size];
        }
    };

    public String getRequiredBatteryState() {
        return requiredBatteryState;
    }

    public void setRequiredBatteryState(String requiredBatteryState) {
        this.requiredBatteryState = requiredBatteryState;
    }

    public Double getStartBatteryLevel() {
        return startBatteryLevel;
    }

    public void setStartBatteryLevel(Double startBatteryLevel) {
        this.startBatteryLevel = startBatteryLevel;
    }

    public Double getEndBatteryLevel() {
        if (endBatteryLevel == 0.0)
            endBatteryLevel = 0.01;
        return endBatteryLevel;
    }

    public void setEndBatteryLevel(Double endBatteryLevel) {
        this.endBatteryLevel = endBatteryLevel;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(requiredBatteryState);
        if (startBatteryLevel == null) {
            dest.writeByte((byte) 0);
        } else {
            dest.writeByte((byte) 1);
            dest.writeDouble(startBatteryLevel);
        }
        if (endBatteryLevel == null) {
            dest.writeByte((byte) 0);
        } else {
            dest.writeByte((byte) 1);
            dest.writeDouble(endBatteryLevel);
        }
    }

    @Override
    public String toString() {
        return "EnergyPreconditionRunStage{" +
                "requiredBatteryState='" + requiredBatteryState + '\'' +
                ", minStartBatteryLevel=" + startBatteryLevel +
                ", minEndBatteryLevel=" + endBatteryLevel +
                '}';
    }
}