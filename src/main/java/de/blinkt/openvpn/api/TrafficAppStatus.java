package de.blinkt.openvpn.api;

import android.os.Parcel;

/**
 * Created by wolong on 25/06/14.
 */
public class TrafficAppStatus {
    public String packageName;
    public long traffic;
    public int otherApps;

    public TrafficAppStatus(String packageName, long traffic, int otherApps) {
        this.packageName = packageName;
        this.traffic = traffic;
        this.otherApps = otherApps;
    }

    public TrafficAppStatus() {
    }

    public static TrafficAppStatus read(Parcel in) {
        TrafficAppStatus instance = new TrafficAppStatus();
        instance.packageName = in.readString();
        instance.traffic = in.readLong();
        instance.otherApps = in.readInt();
        return instance;
    }

    public void write(Parcel out) {
        out.writeString(packageName);
        out.writeLong(traffic);
        out.writeInt(otherApps);
    }
}
