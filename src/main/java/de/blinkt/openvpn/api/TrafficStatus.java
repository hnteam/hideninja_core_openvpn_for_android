package de.blinkt.openvpn.api;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by wolong on 25/06/14.
 */
public class TrafficStatus implements Parcelable {

    private List<TrafficAppStatus> apps;

    public List<TrafficAppStatus> getApps() {
        return apps;
    }

    public TrafficStatus(Parcel in) {
        int appCount = in.readInt();
        apps = new ArrayList<TrafficAppStatus>(appCount);
        for(int i = 0; i < appCount; i++) {
            apps.add(TrafficAppStatus.read(in));
        }
    }

    public TrafficStatus() {
        apps = new ArrayList<TrafficAppStatus>();
    }

    public void addApplication(String packageName, long traffic, int otherApps) {
        apps.add(new TrafficAppStatus(packageName, traffic, otherApps));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int i) {
        out.writeInt(apps.size());
        for(TrafficAppStatus app : apps) {
            app.write(out);
        }
    }

    public static final Parcelable.Creator<TrafficStatus> CREATOR
            = new Parcelable.Creator<TrafficStatus>() {
        public TrafficStatus createFromParcel(Parcel in) {
            return new TrafficStatus(in);
        }

        public TrafficStatus[] newArray(int size) {
            return new TrafficStatus[size];
        }
    };
}
