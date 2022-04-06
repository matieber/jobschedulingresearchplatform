package edu.benchmarkandroid.model;

import android.os.Parcel;
import android.os.Parcelable;

public class UpdateData implements Parcelable {

    private int cpu_mhz;
    private int battery_Mah;
    private double currentBatteryLevel;
    private int rssi;
    private int slotId;

    public UpdateData(int cpu_mhz, int battery_Mah, int rssi, double currentBatteryLevel, int slotId) {
        this.cpu_mhz = cpu_mhz;
        this.battery_Mah = battery_Mah;
        this.currentBatteryLevel = currentBatteryLevel;
        this.rssi = rssi;
        this.slotId = slotId;
    }


    protected UpdateData(Parcel in) {
        cpu_mhz = in.readInt();
        battery_Mah = in.readInt();
        rssi = in.readInt();
        currentBatteryLevel = in.readDouble();
        slotId = in.readInt();
    }

    public static final Creator<UpdateData> CREATOR = new Creator<UpdateData>() {
        @Override
        public UpdateData createFromParcel(Parcel in) {
            return new UpdateData(in);
        }

        @Override
        public UpdateData[] newArray(int size) {
            return new UpdateData[size];
        }
    };

    public int getCpu_mhz() {
        return cpu_mhz;
    }

    public void setCpu_mhz(int cpu_mhz) {
        this.cpu_mhz = cpu_mhz;
    }

    public int getBattery_Mah() {
        return battery_Mah;
    }

    public void setBattery_Mah(int battery_Mah) {
        this.battery_Mah = battery_Mah;
    }

    public double getCurrentBatteryLevel() {
        return currentBatteryLevel;
    }

    public void setCurrentBatteryLevel(double currentBatteryLevel) {
        this.currentBatteryLevel = currentBatteryLevel;
    }

    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(cpu_mhz);
        dest.writeInt(battery_Mah);
        dest.writeInt(rssi);
        dest.writeDouble(currentBatteryLevel);
        dest.writeInt(slotId);
    }

    @Override
    public String toString() {
        return "{ currentBatteryLevel:" + currentBatteryLevel + ", rssi:" + rssi + ", slotId:" + slotId + " }";
    }
}
