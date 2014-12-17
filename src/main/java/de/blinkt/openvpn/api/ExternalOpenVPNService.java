/*
 * Copyright (c) 2012-2014 Arne Schwabe
 * Distributed under the GNU GPL v2. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.api;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;

import com.vpnster.core.ApplicationTrafficCounter;

import java.io.IOException;
import java.io.StringReader;
import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;

import de.blinkt.openvpn.R;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ConfigParser.ConfigParseError;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.OpenVPNService.LocalBinder;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VPNLaunchHelper;
import de.blinkt.openvpn.core.VpnStatus;
import de.blinkt.openvpn.core.VpnStatus.ConnectionStatus;
import de.blinkt.openvpn.core.VpnStatus.StateListener;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
public class ExternalOpenVPNService extends Service implements StateListener {

    private static final int SEND_TOALL = 0;

    final RemoteCallbackList<IOpenVPNStatusCallback> mCallbacks =
            new RemoteCallbackList<IOpenVPNStatusCallback>();

    private OpenVPNService mService;
    private ExternalAppDatabase mExtAppDb;


    private ServiceConnection mConnection = new ServiceConnection() {


        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            LocalBinder binder = (LocalBinder) service;
            mService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mService = null;
        }

    };

    @Override
    public void onCreate() {
        super.onCreate();
        VpnStatus.addStateListener(this);
        mExtAppDb = new ExternalAppDatabase(this);
        mExtAppDb.addApp("com.cryptninja.vpn");

        Intent intent = new Intent(getBaseContext(), OpenVPNService.class);
        intent.setAction(OpenVPNService.START_SERVICE);

        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        mHandler.setService(this);
    }

    private final IOpenVPNAPIService.Stub mBinder = new IOpenVPNAPIService.Stub() {

        private void checkOpenVPNPermission() throws SecurityRemoteException {
            PackageManager pm = getPackageManager();

            for (String apppackage : mExtAppDb.getExtAppList()) {
                ApplicationInfo app;
                try {
                    app = pm.getApplicationInfo(apppackage, 0);
                    if (Binder.getCallingUid() == app.uid) {
                        return;
                    }
                } catch (NameNotFoundException e) {
                    // App not found. Remove it from the list
                    mExtAppDb.removeApp(apppackage);
                }

            }
            throw new SecurityException("Unauthorized OpenVPN API Caller");
        }

        @Override
        public List<APIVpnProfile> getProfiles() throws RemoteException {
            checkOpenVPNPermission();

            ProfileManager pm = ProfileManager.getInstance(getBaseContext());

            List<APIVpnProfile> profiles = new LinkedList<APIVpnProfile>();

            for (VpnProfile vp : pm.getProfiles())
                profiles.add(new APIVpnProfile(vp.getUUIDString(), vp.mName, vp.mUserEditable));

            return profiles;
        }


        private void startProfile(VpnProfile vp)
        {
            Intent vpnPermissionIntent = VpnService.prepare(ExternalOpenVPNService.this);
            /* Check if we need to show the confirmation dialog */
            if(vpnPermissionIntent != null){
                Intent shortVPNIntent = new Intent(Intent.ACTION_MAIN);
                shortVPNIntent.setClass(getBaseContext(), de.blinkt.openvpn.LaunchVPN.class);
                shortVPNIntent.putExtra(de.blinkt.openvpn.LaunchVPN.EXTRA_KEY, vp.getUUIDString());
                shortVPNIntent.putExtra(de.blinkt.openvpn.LaunchVPN.EXTRA_HIDELOG, true);
                shortVPNIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(shortVPNIntent);
            } else {
                VPNLaunchHelper.startOpenVpn(vp, getBaseContext());
            }

        }

        @Override
        public void startProfile(String profileUUID) throws RemoteException {
            checkOpenVPNPermission();

            VpnProfile vp = ProfileManager.get(getBaseContext(), profileUUID);
            startProfile(vp);
        }

        public void startVPN(String inlineconfig) throws RemoteException {
            checkOpenVPNPermission();

            ConfigParser cp = new ConfigParser();
            try {
                cp.parseConfig(new StringReader(inlineconfig));
                VpnProfile vp = cp.convertProfile();
                if (vp.checkProfile(getApplicationContext()) != R.string.no_error_found)
                    throw new RemoteException(getString(vp.checkProfile(getApplicationContext())));


                ProfileManager.setTemporaryProfile(vp);
                startProfile(vp);

            } catch (IOException e) {
                throw new RemoteException(e.getMessage());
            } catch (ConfigParseError e) {
                throw new RemoteException(e.getMessage());
            }
        }



        @Override
        public boolean addVPNProfile(String name, String config) throws RemoteException {
            checkOpenVPNPermission();

            ConfigParser cp = new ConfigParser();
            try {
                cp.parseConfig(new StringReader(config));
                VpnProfile vp = cp.convertProfile();
                vp.mName = name;
                ProfileManager pm = ProfileManager.getInstance(getBaseContext());
                pm.addProfile(vp);
            } catch (IOException e) {
                VpnStatus.logException(e);
                return false;
            } catch (ConfigParseError e) {
                VpnStatus.logException(e);
                return false;
            }

            return true;
        }


        public void removeProfile(String profileUUID) throws RemoteException {
            checkOpenVPNPermission();
            ProfileManager pm = ProfileManager.getInstance(getBaseContext());
            VpnProfile vp = ProfileManager.get(getBaseContext(), profileUUID);
            pm.removeProfile(ExternalOpenVPNService.this, vp);
        }



        public Intent prepare(String packagename) {
            if (new ExternalAppDatabase(ExternalOpenVPNService.this).isAllowed(packagename))
                return null;

            Intent intent = new Intent();
            intent.setClass(ExternalOpenVPNService.this, ConfirmDialog.class);
            return intent;
        }


        public Intent prepareVPNService() throws RemoteException {
            checkOpenVPNPermission();

            if (VpnService.prepare(ExternalOpenVPNService.this) == null)
                return null;
            else
                return new Intent(getBaseContext(), GrantPermissionsActivity.class);
        }


        @Override
        public void registerStatusCallback(IOpenVPNStatusCallback cb)
                throws RemoteException {
            checkOpenVPNPermission();

            if (cb != null) {
                cb.newStatus(mMostRecentState.vpnUUID, mMostRecentState.state,
                        mMostRecentState.logmessage, mMostRecentState.level.name());
                mCallbacks.register(cb);
            }


        }

        @Override
        public void unregisterStatusCallback(IOpenVPNStatusCallback cb)
                throws RemoteException {
            checkOpenVPNPermission();

            if (cb != null)
                mCallbacks.unregister(cb);
        }


        public void disconnect() throws RemoteException {
            checkOpenVPNPermission();
            if (mService != null && mService.getManagement() != null)
                mService.getManagement().stopVPN();
        }

        @Override
        public void pause() throws RemoteException {
            checkOpenVPNPermission();
            if (mService != null)
                mService.userPause(true);
        }

        @Override
        public void resume() throws RemoteException {
            checkOpenVPNPermission();
            if (mService != null)
                mService.userPause(false);

        }

        public String getLastState() {
            return VpnStatus.getLastState();
        }


        public TrafficStatus getTrafficStats() throws RemoteException {
            final ApplicationTrafficCounter counter = ApplicationTrafficCounter.getInstance();
            if (counter == null) {
                return null;
            }

            return counter.getTrafficStatus();
        }
    };


    private UpdateMessage mMostRecentState;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mCallbacks.kill();
        unbindService(mConnection);
        VpnStatus.removeStateListener(this);
    }

    class UpdateMessage {
        public String state;
        public String logmessage;
        public ConnectionStatus level;
        public String vpnUUID;

        public UpdateMessage(String state, String logmessage, ConnectionStatus level) {
            this.state = state;
            this.logmessage = logmessage;
            this.level = level;
        }
    }

    @Override
    public void updateState(String state, String logmessage, int resid, ConnectionStatus level) {
        mMostRecentState = new UpdateMessage(state, logmessage, level);
        if (ProfileManager.getLastConnectedVpn() != null)
            mMostRecentState.vpnUUID = ProfileManager.getLastConnectedVpn().getUUIDString();

        Message msg = mHandler.obtainMessage(SEND_TOALL, mMostRecentState);
        msg.sendToTarget();

    }

    private static final OpenVPNServiceHandler mHandler = new OpenVPNServiceHandler();


    static class OpenVPNServiceHandler extends Handler {
        WeakReference<ExternalOpenVPNService> service = null;

        private void setService(ExternalOpenVPNService eos) {
            service = new WeakReference<ExternalOpenVPNService>(eos);
        }

        @Override
        public void handleMessage(Message msg) {

            RemoteCallbackList<IOpenVPNStatusCallback> callbacks;
            switch (msg.what) {
                case SEND_TOALL:
                    if (service == null || service.get() == null)
                        return;

                    callbacks = service.get().mCallbacks;


                    // Broadcast to all clients the new value.
                    final int N = callbacks.beginBroadcast();
                    for (int i = 0; i < N; i++) {
                        try {
                            sendUpdate(callbacks.getBroadcastItem(i), (UpdateMessage) msg.obj);
                        } catch (RemoteException e) {
                            // The RemoteCallbackList will take care of removing
                            // the dead object for us.
                        }
                    }
                    callbacks.finishBroadcast();
                    break;
            }
        }

        private void sendUpdate(IOpenVPNStatusCallback broadcastItem,
                                UpdateMessage um) throws RemoteException {
            broadcastItem.newStatus(um.vpnUUID, um.state, um.logmessage, um.level.name());
        }
    }
}
