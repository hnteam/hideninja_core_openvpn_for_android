package com.vpnster.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.blinkt.openvpn.api.TrafficStatus;

/**
 * Created by wolong on 25/06/14.
 */
public class ApplicationTrafficCounter implements Runnable {

    private static ApplicationTrafficCounter instance;
    private Context context;
    private boolean shutdowned = false;
    private boolean running = false;
    private HashMap<Integer, ApplicationTrafficEntry> applicationsMap = new HashMap<Integer, ApplicationTrafficEntry>();

    private volatile Thread counterThread;

    private ApplicationTrafficCounter(Context context) {
        this.context = context;
    }

    public static ApplicationTrafficCounter getInstance() {
        return instance;
    }

    public static ApplicationTrafficCounter getInstance(Context context) {
        if(instance == null) {
            synchronized (ApplicationTrafficCounter.class) {
                if(instance == null) {
                    instance = new ApplicationTrafficCounter(context);
                    instance.init();
                }
            }
        } else {
            if(instance.context != context) {
                synchronized (ApplicationTrafficCounter.class) {
                    if(instance.context != context) {
                        instance.stop();
                        instance.shutdown();

                        instance = new ApplicationTrafficCounter(context);
                        instance.init();
                    }
                }
            }
        }

        return instance;
    }

    public Map<Integer, ApplicationTrafficEntry> getStats() {
        return applicationsMap;
    }

    private void init() {

    }

    private void shutdown() {
        this.shutdowned = true;
    }

    public void start() {

        if(running) {
            return;
        }

        running = true;

        if(counterThread != null) {
            counterThread.interrupt();
        }

        applicationsMap.clear();

        final PackageManager packageManager = context.getPackageManager();
        if(packageManager == null) {
            return;
        }

        final List<ApplicationInfo> applications = packageManager.getInstalledApplications(0);


        for(ApplicationInfo application : applications) {
            if(application.packageName.startsWith("com.vpnster")) {
                continue;
            }

            if(application.packageName.startsWith("com.roibax")) {
                continue;
            }

            if(application.packageName.startsWith("com.hideninja")) {
                continue;
            }

            if(application.packageName.startsWith("com.cryptninja")) {
                continue;
            }

            int uid = application.uid;
            ApplicationTrafficEntry applicationTrafficEntry = applicationsMap.get(uid);

            if(applicationTrafficEntry == null) {
                applicationTrafficEntry = new ApplicationTrafficEntry(uid);
                applicationsMap.put(uid, applicationTrafficEntry);
            }

            applicationTrafficEntry.addApplication(application);
        }

        counterThread = new Thread(this);
        counterThread.start();
    }

    public void stop() {
        if(!running) {
            return;
        }

        running = false;
        counterThread.interrupt();

        applicationsMap.clear();
    }


    @Override
    public void run() {
        try {
            for (ApplicationTrafficEntry app : applicationsMap.values()) {
                app.startStats();
            }

            while (running) {
                Thread.sleep(3000);

                for (ApplicationTrafficEntry app : applicationsMap.values()) {
                    app.countStats();
                }
            }
        } catch (Exception e) {
            Log.e("COUNTER", "traffic counter thread interrupted");
        }
    }

    public TrafficStatus getTrafficStatus() {
        final TrafficStatus status = new TrafficStatus();
        final Map<Integer, ApplicationTrafficEntry> stats = getStats();

        for (ApplicationTrafficEntry appEntry : stats.values()) {
            final String firstPackage = appEntry.getApplications().get(0).packageName;
            final int otherApps = appEntry.getApplications().size() - 1;

            status.addApplication(firstPackage, appEntry.getTraffic(), otherApps);
        }

        return status;
    }
}
