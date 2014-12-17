package com.vpnster.core;

import android.content.pm.ApplicationInfo;
import android.net.TrafficStats;

import com.google.gson.annotations.Expose;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by wolong on 25/06/14.
 */
public class ApplicationTrafficEntry {
    @Expose
    private List<ApplicationInfo> applications = new ArrayList<ApplicationInfo>();
    private int uid;
    private long originTx;
    private long originRx;
    private long tx = 0;
    private long rx = 0;

    public ApplicationTrafficEntry(int uid) {
        this.uid = uid;
    }

    public boolean isSystem() {
        return uid < 10000;
    }

    public List<ApplicationInfo> getApplications() {
        return applications;
    }

    public long getTraffic() {
        return tx + rx;
    }

    public void clearApplications() {
        applications.clear();
    }

    public void addApplication(ApplicationInfo applicationInfo) {
        applications.add(applicationInfo);
    }

    public void startStats() {
        this.originTx = TrafficStats.getUidTxBytes(uid);
        this.originRx = TrafficStats.getUidRxBytes(uid);
    }

    public void countStats() {
        long currentTx = TrafficStats.getUidTxBytes(uid);
        long currentRx = TrafficStats.getUidRxBytes(uid);

        this.tx += currentTx - originTx;
        this.rx += currentRx - originRx;

        originTx = currentTx;
        originRx = currentRx;
    }
}
