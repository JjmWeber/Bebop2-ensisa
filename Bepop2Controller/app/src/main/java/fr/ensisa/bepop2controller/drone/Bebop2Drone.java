package fr.ensisa.bepop2controller.drone;

import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DICTIONARY_KEY_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_ERROR_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerArgumentDictionary;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARControllerDictionary;
import com.parrot.arsdk.arcontroller.ARControllerException;
import com.parrot.arsdk.arcontroller.ARDeviceController;
import com.parrot.arsdk.arcontroller.ARDeviceControllerListener;
import com.parrot.arsdk.arcontroller.ARDeviceControllerStreamListener;
import com.parrot.arsdk.arcontroller.ARFeatureARDrone3;
import com.parrot.arsdk.arcontroller.ARFeatureCommon;
import com.parrot.arsdk.arcontroller.ARFrame;
import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_ENUM;
import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_FAMILY_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDevice;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceNetService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryException;
import com.parrot.arsdk.ardiscovery.ARDiscoveryService;
import com.parrot.arsdk.arutils.ARUtilsException;
import com.parrot.arsdk.arutils.ARUtilsManager;

import java.util.ArrayList;
import java.util.List;

public class Bebop2Drone {

    private static final String TAG = "Bebop2Drone";
    private static final int DEVICE_PORT = 21;

    public interface Listener {
        void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state);

        void onBatteryChargeChanged(int battery);

        void onAltitudeChanged(double altitude);

        void onSpeedChanged(float speed);

        void horizonChanged(float roll);

        void onPilotingStateChanged(ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state);

        void onPictureTaken(ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error);

        void configureDecoder(ARControllerCodec codec);

        void onFrameReceived(ARFrame frame);

        void onMatchingMediasFound(int nbMedias);

        void onDownloadProgressed(String mediaName, int progress);

        void onDownloadComplete(String mediaName);
    }

    private final List<Listener> listeners;
    private final Handler handler;

    private ARDeviceController deviceController;
    private SDCardModule sdCardModule;
    private ARCONTROLLER_DEVICE_STATE_ENUM state;
    private ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM flyingState;

    private String currentRunId;

    public Bebop2Drone(Context context, @NonNull ARDiscoveryDeviceService deviceService) {

        listeners = new ArrayList<>();
        handler = new Handler(context.getMainLooper());
        state = ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED;

        ARDISCOVERY_PRODUCT_ENUM productType = ARDiscoveryService.getProductFromProductID(deviceService.getProductID());
        ARDISCOVERY_PRODUCT_FAMILY_ENUM family = ARDiscoveryService.getProductFamily(productType);
        if(ARDISCOVERY_PRODUCT_FAMILY_ENUM.ARDISCOVERY_PRODUCT_FAMILY_ARDRONE.equals(family)) {

            ARDiscoveryDevice discoveryDevice = createDiscoveryDevice(deviceService, productType);
            if(discoveryDevice != null) {
                deviceController = createDeviceController(discoveryDevice);
                discoveryDevice.dispose();
            }

            try {
                String productIP = ((ARDiscoveryDeviceNetService)(deviceService.getDevice())).getIp();

                ARUtilsManager ftpListManager = new ARUtilsManager();
                ARUtilsManager ftpQueueManager = new ARUtilsManager();

                ftpListManager.initWifiFtp(productIP, DEVICE_PORT, ARUtilsManager.FTP_ANONYMOUS, "");
                ftpQueueManager.initWifiFtp(productIP, DEVICE_PORT, ARUtilsManager.FTP_ANONYMOUS, "");

                sdCardModule = new SDCardModule(ftpListManager, ftpQueueManager);
                sdCardModule.addListener(sdCardModuleListener);
            }
            catch (ARUtilsException e) {
                Log.e(TAG, "Exception", e);
            }

        } else {
            Log.e(TAG, "DeviceService type is not supported by Bebop2Drone");
        }
    }

    public void dispose() {
        if(deviceController != null)
            deviceController.dispose();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public boolean connect() {
        boolean success = false;
        if((deviceController != null) && (ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED.equals(state))) {
            ARCONTROLLER_ERROR_ENUM error = deviceController.start();
            if(error == ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                success = true;
            }
        }
        return success;
    }

    public boolean disconnect() {
        boolean success = false;
        if((deviceController != null) && (ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(state))) {
            ARCONTROLLER_ERROR_ENUM error = deviceController.stop();
            if(error == ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                success = true;
            }
        }
        return success;
    }

    public ARCONTROLLER_DEVICE_STATE_ENUM getConnectionState() {
        return state;
    }

    public ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM getFlyingState() {
        return flyingState;
    }

    public ARDeviceController getDeviceController() {
        return deviceController;
    }

    public void takeOff() {
        if((deviceController != null) && (state.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING)))
            deviceController.getFeatureARDrone3().sendPilotingTakeOff();
    }

    public void land() {
        if((deviceController != null) && (state.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING)))
            deviceController.getFeatureARDrone3().sendPilotingLanding();
    }

    public void emergency() {
        if((deviceController != null) && (state.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING)))
            deviceController.getFeatureARDrone3().sendPilotingEmergency();
    }

    public void takePicture() {
        if((deviceController != null) && (state.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING)))
            deviceController.getFeatureARDrone3().sendMediaRecordPictureV2();
    }

    public void setPitch(byte pitch) {
        if((deviceController != null) && (state.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING)))
            deviceController.getFeatureARDrone3().setPilotingPCMDPitch(pitch);
    }

    public void setRoll(byte roll) {
        if((deviceController != null) && (state.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING)))
            deviceController.getFeatureARDrone3().setPilotingPCMDRoll(roll);
    }

    public void setYaw(byte yaw) {
        if((deviceController != null) && (state.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING)))
            deviceController.getFeatureARDrone3().setPilotingPCMDYaw(yaw);
    }

    public void setGaz(byte gaz) {
        if((deviceController != null) && (state.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING)))
            deviceController.getFeatureARDrone3().setPilotingPCMDGaz(gaz);
    }

    public void setFlag(byte flag) {
        if((deviceController != null) && (state.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING)))
            deviceController.getFeatureARDrone3().setPilotingPCMDFlag(flag);
    }

    public void getLastFlightMedias() {
        String runId = currentRunId;
        if((runId != null) && !runId.isEmpty())
            sdCardModule.getFlightMedias(runId);
        else {
            Log.e(TAG, "RunID not available, fallback to the day's medias");
            sdCardModule.getTodaysFlightMedias();
        }
    }

    public void cancelGetLastFlightMedias() {
        sdCardModule.cancelGetFlightMedias();
    }

    private ARDiscoveryDevice createDiscoveryDevice(@NonNull ARDiscoveryDeviceService service, ARDISCOVERY_PRODUCT_ENUM productType) {
        ARDiscoveryDevice device = null;

        try {
            device = new ARDiscoveryDevice();

            ARDiscoveryDeviceNetService netDeviceService = (ARDiscoveryDeviceNetService) service.getDevice();
            device.initWifi(productType, netDeviceService.getName(), netDeviceService.getIp(), netDeviceService.getPort());
        } catch (ARDiscoveryException e) {
            Log.e(TAG, "Exception", e);
            Log.e(TAG, "Error: " + e.getError());
        }

        return device;
    }

    private ARDeviceController createDeviceController(@NonNull ARDiscoveryDevice discoveryDevice) {
        ARDeviceController deviceController = null;

        try {
            deviceController = new ARDeviceController(discoveryDevice);

            deviceController.addListener(deviceControllerListener);
            deviceController.addStreamListener(streamListener);
        } catch (ARControllerException e) {
            Log.e(TAG, "Exception", e);
        }

        return deviceController;
    }

    private void notifyConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
        List<Listener> listeners = new ArrayList<>(this.listeners);
        for (Listener listener : listeners)
            listener.onDroneConnectionChanged(state);
    }

    private void notifyBatteryChanged(int battery) {
        List<Listener> listeners = new ArrayList<>(this.listeners);
        for (Listener listener : listeners)
            listener.onBatteryChargeChanged(battery);
    }

    private void notifyAltitudeChanged(double altitude) {
        List<Listener> listeners = new ArrayList<>(this.listeners);
        for (Listener listener : listeners)
            listener.onAltitudeChanged(altitude);
    }

    private void notifySpeedChanged(float speed) {
        List<Listener> listenersCpy = new ArrayList<>(this.listeners);
        for (Listener listener : listenersCpy) {
            listener.onSpeedChanged(speed);
        }
    }

    private void notifyHorizonChanged(float roll) {
        List<Listener> listenersCpy = new ArrayList<>(this.listeners);
        for (Listener listener : listenersCpy) {
            listener.horizonChanged(roll);
        }
    }

    private void notifyPilotingStateChanged(ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state) {
        List<Listener> listeners = new ArrayList<>(this.listeners);
        for (Listener listener : listeners)
            listener.onPilotingStateChanged(state);
    }

    private void notifyPictureTaken(ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error) {
        List<Listener> listeners = new ArrayList<>(this.listeners);
        for (Listener listener : listeners)
            listener.onPictureTaken(error);
    }

    private void notifyConfigureDecoder(ARControllerCodec codec) {
        List<Listener> listeners = new ArrayList<>(this.listeners);
        for (Listener listener : listeners)
            listener.configureDecoder(codec);
    }

    private void notifyFrameReceived(ARFrame frame) {
        List<Listener> listeners = new ArrayList<>(this.listeners);
        for (Listener listener : listeners)
            listener.onFrameReceived(frame);
    }

    private void notifyMatchingMediasFound(int nbMedias) {
        List<Listener> listeners = new ArrayList<>(this.listeners);
        for (Listener listener : listeners)
            listener.onMatchingMediasFound(nbMedias);
    }

    private void notifyDownloadProgressed(String mediaName, int progress) {
        List<Listener> listeners = new ArrayList<>(this.listeners);
        for (Listener listener : listeners)
            listener.onDownloadProgressed(mediaName, progress);
    }

    private void notifyDownloadComplete(String mediaName) {
        List<Listener> listeners = new ArrayList<>(this.listeners);
        for (Listener listener : listeners)
            listener.onDownloadComplete(mediaName);
    }

    @SuppressWarnings("FieldCanBeLocal")
    private final SDCardModule.Listener sdCardModuleListener = new SDCardModule.Listener() {
        @Override
        public void onMatchingMediasFound(final int nbMedias) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    notifyMatchingMediasFound(nbMedias);
                }
            });
        }

        @Override
        public void onDownloadProgressed(final String mediaName, final int progress) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    notifyDownloadProgressed(mediaName, progress);
                }
            });
        }

        @Override
        public void onDownloadComplete(final String mediaName) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    notifyDownloadComplete(mediaName);
                }
            });
        }
    };

    private final ARDeviceControllerListener deviceControllerListener = new ARDeviceControllerListener() {
        @Override
        public void onStateChanged(ARDeviceController deviceController, ARCONTROLLER_DEVICE_STATE_ENUM newState, ARCONTROLLER_ERROR_ENUM error) {
            state = newState;
            if(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(state))
                Bebop2Drone.this.deviceController.getFeatureARDrone3().sendMediaStreamingVideoEnable((byte) 1);
            else if(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED.equals(state))
                sdCardModule.cancelGetFlightMedias();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    notifyConnectionChanged(state);
                }
            });
        }

        @Override
        public void onExtensionStateChanged(ARDeviceController deviceController, ARCONTROLLER_DEVICE_STATE_ENUM newState, ARDISCOVERY_PRODUCT_ENUM product, String name, ARCONTROLLER_ERROR_ENUM error) {

        }

        @Override
        public void onCommandReceived(ARDeviceController deviceController, ARCONTROLLER_DICTIONARY_KEY_ENUM commandKey, ARControllerDictionary elementDictionary) {
            // if event received is the battery update
            if((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if(args != null) {
                    final int battery = (Integer) args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED_PERCENT);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyBatteryChanged(battery);
                        }
                    });
                }
            }
            // if event received is the altitude update
            else if((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ALTITUDECHANGED) && (elementDictionary != null)){
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if(args != null) {
                    final double altitude = (double)args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ALTITUDECHANGED_ALTITUDE);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyAltitudeChanged(altitude);
                        }
                    });
                }
            }
            // if event received is the horizon update
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ATTITUDECHANGED) && (elementDictionary != null)){
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final float roll = (float)((Double)args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ATTITUDECHANGED_ROLL)).doubleValue();
                    //float pitch = (float)((Double)args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ATTITUDECHANGED_PITCH)).doubleValue();
                    //float yaw = (float)((Double)args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ATTITUDECHANGED_YAW)).doubleValue();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyHorizonChanged(roll);
                        }
                    });
                }
            }
            // if event received is the speed update
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_SPEEDCHANGED) && (elementDictionary != null)){
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final  float speedX = (float)((Double)args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_SPEEDCHANGED_SPEEDX)).doubleValue();
                    //final float speedY = (float)((Double)args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_SPEEDCHANGED_SPEEDY)).doubleValue();
                    //final float speedZ = (float)((Double)args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_SPEEDCHANGED_SPEEDZ)).doubleValue();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifySpeedChanged(speedX);
                        }
                    });
                }
            }
            // if event received is the flying state update
            else if((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if(args != null) {
                    final ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state = ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM.getFromValue((Integer) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE));
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            flyingState = state;
                            notifyPilotingStateChanged(state);
                        }
                    });
                }
            }
            // if event received is the picture notification
            else if((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED) && (elementDictionary != null)){
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if(args != null) {
                    final ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error = ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM.getFromValue((Integer)args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR));
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyPictureTaken(error);
                        }
                    });
                }
            }
            // if event received is the run id
            else if((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_RUNSTATE_RUNIDCHANGED) && (elementDictionary != null)){
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if(args != null) {
                    final String runID = (String) args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_RUNSTATE_RUNIDCHANGED_RUNID);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            currentRunId = runID;
                        }
                    });
                }
            }

        }
    };

    private final ARDeviceControllerStreamListener streamListener = new ARDeviceControllerStreamListener() {
        @Override
        public ARCONTROLLER_ERROR_ENUM configureDecoder(ARDeviceController deviceController, final ARControllerCodec codec) {
            notifyConfigureDecoder(codec);
            return ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
        }

        @Override
        public ARCONTROLLER_ERROR_ENUM onFrameReceived(ARDeviceController deviceController, final ARFrame frame) {
            notifyFrameReceived(frame);
            return ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
        }

        @Override
        public void onFrameTimeout(ARDeviceController deviceController) {

        }
    };

}
