package fr.ensisa.bebop2controller.discovery;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryService;
import com.parrot.arsdk.ardiscovery.receivers.ARDiscoveryServicesDevicesListUpdatedReceiver;
import com.parrot.arsdk.ardiscovery.receivers.ARDiscoveryServicesDevicesListUpdatedReceiverDelegate;

import java.util.ArrayList;
import java.util.List;

public class DroneDiscoverer {

    private static final String TAG = "DroneDiscoverer";

    private final ARDiscoveryServicesDevicesListUpdatedReceiver ardiscoveryServicesDevicesListUpdatedReceiver;
    private final List<ARDiscoveryDeviceService> matchingDrones;
    private final List<Listener> listeners;
    private final Context context;
    @SuppressWarnings("SpellCheckingInspection")
    private ServiceConnection ardiscoveryServiceConnection;
    private ARDiscoveryService ardiscoveryService;
    private boolean startDiscoveryAfterConnection;

    public interface Listener {
        void onDronesListUpdated(List<ARDiscoveryDeviceService> dronesList);
    }

    public DroneDiscoverer(Context context) {
        this.context = context;
        listeners = new ArrayList<>();
        matchingDrones = new ArrayList<>();
        ardiscoveryServicesDevicesListUpdatedReceiver = new ARDiscoveryServicesDevicesListUpdatedReceiver(discoveryListener);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
        notifyServiceDiscovered(matchingDrones);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void setup() {
        LocalBroadcastManager localBroadcastMgr = LocalBroadcastManager.getInstance(context);
        localBroadcastMgr.registerReceiver(ardiscoveryServicesDevicesListUpdatedReceiver,
                new IntentFilter(ARDiscoveryService.kARDiscoveryServiceNotificationServicesDevicesListUpdated));

        if(ardiscoveryServiceConnection == null)
            ardiscoveryServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    ardiscoveryService = ((ARDiscoveryService.LocalBinder) service).getService();

                    if(startDiscoveryAfterConnection) {
                        startDiscovering();
                        startDiscoveryAfterConnection = false;
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    ardiscoveryService = null;
                }
            };

        if(ardiscoveryService == null) {
            Intent i = new Intent(context, ARDiscoveryService.class);
            context.bindService(i, ardiscoveryServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    public void cleanup() {
        stopDiscovering();
        Log.d(TAG, "closeServices ...");

        if(ardiscoveryService != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    ardiscoveryService.stop();
                    context.unbindService(ardiscoveryServiceConnection);
                    ardiscoveryService = null;
                }
            }).start();
        }
        
        LocalBroadcastManager localBroadcastMgr = LocalBroadcastManager.getInstance(context);
        localBroadcastMgr.unregisterReceiver(ardiscoveryServicesDevicesListUpdatedReceiver);
    }

    public void startDiscovering() {
        if(ardiscoveryService != null) {
            Log.i(TAG, "Start discovering");
            discoveryListener.onServicesDevicesListUpdated();
            ardiscoveryService.start();
            startDiscoveryAfterConnection = false;
        } else
            startDiscoveryAfterConnection = true;
    }

    public void stopDiscovering() {
        if(ardiscoveryService != null) {
            Log.i(TAG, "Stop discovering");
            ardiscoveryService.stop();
        }
        startDiscoveryAfterConnection = false;
    }

    private void notifyServiceDiscovered(List<ARDiscoveryDeviceService> dronesList) {
        List<Listener> listeners = new ArrayList<>(this.listeners);
        for (Listener listener : listeners)
            listener.onDronesListUpdated(dronesList);
    }

    private final ARDiscoveryServicesDevicesListUpdatedReceiverDelegate discoveryListener =
            new ARDiscoveryServicesDevicesListUpdatedReceiverDelegate() {
                @Override
                public void onServicesDevicesListUpdated() {
                    if(ardiscoveryService != null) {
                        matchingDrones.clear();
                        List<ARDiscoveryDeviceService> deviceList = ardiscoveryService.getDeviceServicesArray();

                        if(deviceList != null)
                            for (ARDiscoveryDeviceService service : deviceList)
                                matchingDrones.add(service);
                        notifyServiceDiscovered(matchingDrones);
                    }
                }
            };

}
