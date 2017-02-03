package fr.ensisa.bebop2controller.drone;

import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_GPSSTATE_HOMETYPEAVAILABILITYCHANGED_TYPE_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_GPSSTATE_HOMETYPECHOSENCHANGED_TYPE_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_REASON_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONAXISTOCALIBRATECHANGED_AXIS_ENUM;
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
        void onAltitudeChanged(double altitude);

        void onAxisForCalibrationChanged(ARCOMMANDS_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONAXISTOCALIBRATECHANGED_AXIS_ENUM axis);

        void onBatteryChargeChanged(int charge);

        void onCameraSettingsChanged(float fov, float panMax, float panMin, float tiltMax, float tiltMin);

        void onCodecConfigured(ARControllerCodec codec);

        void onDownloadCompleted(String mediaName);

        void onDownloadProgressed(String mediaName, int progress);

        void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state);

        void onFrameReceived(ARFrame frame);

        void onGPSFixedStateChanged(byte fixed);

        void onHomeTypeAvailabilityChanged(ARCOMMANDS_ARDRONE3_GPSSTATE_HOMETYPEAVAILABILITYCHANGED_TYPE_ENUM type, byte available);

        void onHomeTypeChosenChanged(ARCOMMANDS_ARDRONE3_GPSSTATE_HOMETYPECHOSENCHANGED_TYPE_ENUM type);

        void onHorizonChanged(float roll, float pitch, float yaw);

        void onMatchingMediasFound(int nbMedias);

        void onMaxRotationSpeedChanged(float current, float min, float max);

        void onNavigateHomeStateChanged(ARCOMMANDS_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_STATE_ENUM state,
                                        ARCOMMANDS_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_REASON_ENUM reason);

        void onPictureTaken(ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error);

        void onPilotingStateChanged(ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state);

        void onResetHomeChanged(double latitude, double longitude, double altitude);

        void onSpeedChanged(double speed);

        void onVideoStateChanged(ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE_ENUM state);
    }

    private final List<Listener> listeners;
    private final Handler handler;
    private String currentRunId;
    private SDCardModule sdCardModule;
    private ARDeviceController deviceController;
    private ARCONTROLLER_DEVICE_STATE_ENUM state;
    private ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM flyingState;

    public Bebop2Drone(Context context, @NonNull ARDiscoveryDeviceService deviceService) {
        listeners = new ArrayList<>();
        handler = new Handler(context.getMainLooper());
        state = ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED;

        ARDISCOVERY_PRODUCT_ENUM productType = ARDiscoveryService.getProductFromProductID(deviceService.getProductID());
        ARDISCOVERY_PRODUCT_FAMILY_ENUM family = ARDiscoveryService.getProductFamily(productType);
        if (ARDISCOVERY_PRODUCT_FAMILY_ENUM.ARDISCOVERY_PRODUCT_FAMILY_ARDRONE.equals(family)) {

            ARDiscoveryDevice discoveryDevice = createDiscoveryDevice(deviceService, productType);
            if (discoveryDevice != null) {
                deviceController = createDeviceController(discoveryDevice);
                discoveryDevice.dispose();
            }

            try {
                String productIP = ((ARDiscoveryDeviceNetService) (deviceService.getDevice())).getIp();

                ARUtilsManager ftpListManager = new ARUtilsManager();
                ARUtilsManager ftpQueueManager = new ARUtilsManager();

                ftpListManager.initWifiFtp(productIP, DEVICE_PORT, ARUtilsManager.FTP_ANONYMOUS, "");
                ftpQueueManager.initWifiFtp(productIP, DEVICE_PORT, ARUtilsManager.FTP_ANONYMOUS, "");

                sdCardModule = new SDCardModule(ftpListManager, ftpQueueManager);
                sdCardModule.addListener(sdCardModuleListener);
            } catch (ARUtilsException e) {
                Log.e(TAG, "Exception", e);
            }

        } else {
            Log.e(TAG, "DeviceService type is not supported by Bebop2Drone");
        }
    }

    public void dispose() {
        if (deviceController != null)
            deviceController.dispose();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    @SuppressWarnings("unused")
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public boolean connect() {
        boolean success = false;
        if ((deviceController != null) && (ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED.equals(state))) {
            ARCONTROLLER_ERROR_ENUM error = deviceController.start();
            if (error == ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK)
                success = true;
        }
        return success;
    }

    public boolean disconnect() {
        boolean success = false;
        if ((deviceController != null) && (ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(state))) {
            ARCONTROLLER_ERROR_ENUM error = deviceController.stop();
            if (error == ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK)
                success = true;
        }
        return success;
    }

    public ARCONTROLLER_DEVICE_STATE_ENUM getConnectionState() {
        return state;
    }

    public ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM getFlyingState() {
        return flyingState;
    }

    private ARDeviceController getDeviceController() {
        return deviceController;
    }

    public void cancelGetLastFlightMedias() {
        sdCardModule.cancelGetFlightMedias();
    }

    public void doAFlatTrim() {
        if (deviceController != null)
            deviceController.getFeatureARDrone3().sendPilotingFlatTrim();
    }

    public void emergency() {
        if ((deviceController != null) && (state.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING)))
            deviceController.getFeatureARDrone3().sendPilotingEmergency();
    }

    public void getLastFlightMedias() {
        String runId = currentRunId;
        if ((runId != null) && !runId.isEmpty()) {
            sdCardModule.getFlightMedias(runId);
        } else {
            Log.e(TAG, "RunID not available, fallback to the day's medias");
            sdCardModule.getTodaysFlightMedias();
        }
    }

    public void goHome() {
        if (deviceController != null && (state.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING)))
            deviceController.getFeatureARDrone3().sendPilotingNavigateHome((byte) 1);
    }

    public void land() {
        if ((deviceController != null) && (state.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING)))
            deviceController.getFeatureARDrone3().sendPilotingLanding();
    }

    public void makeAFlip(ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_ENUM direction) {
        if ((deviceController != null) && (state.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING)))
            getDeviceController().getFeatureARDrone3().sendAnimationsFlip(direction);
    }

    public void setAutoRecordMode(boolean mode) {
        if (deviceController != null)
            deviceController.getFeatureARDrone3().sendPictureSettingsVideoAutorecordSelection((byte) (mode ? 1 : 0), (byte) 0);
    }

    @SuppressWarnings("unused")
    public void setCameraOrientation(float tilt) {
        if (deviceController != null)
            deviceController.getFeatureARDrone3().sendCameraOrientationV2(tilt, 0);
    }

    public void setFlag(byte flag) {
        if ((deviceController != null) && (state.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING)))
            deviceController.getFeatureARDrone3().setPilotingPCMDFlag(flag);
    }

    public void setGaz(byte gaz) {
        if ((deviceController != null) && (state.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING)))
            deviceController.getFeatureARDrone3().setPilotingPCMDGaz(gaz);
    }

    public void setGPSHomeType(ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_ENUM type) {
        if (deviceController != null)
            deviceController.getFeatureARDrone3().sendGPSSettingsHomeType(type);
    }

    public void setLinearSpeed(float linearSpeed) {
        if (deviceController != null)
            deviceController.getFeatureARDrone3().sendSpeedSettingsMaxPitchRollRotationSpeed(linearSpeed);
    }

    public void setPitch(byte pitch) {
        if ((deviceController != null) && (state.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING)))
            deviceController.getFeatureARDrone3().setPilotingPCMDPitch(pitch);
    }

    public void setRoll(byte roll) {
        if ((deviceController != null) && (state.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING)))
            deviceController.getFeatureARDrone3().setPilotingPCMDRoll(roll);
    }

    public void setRotationSpeed(Float rotationSpeed) {
        if (deviceController != null)
            deviceController.getFeatureARDrone3().sendSpeedSettingsMaxRotationSpeed(rotationSpeed);
    }

    public void setYaw(byte yaw) {
        if ((deviceController != null) && (state.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING)))
            deviceController.getFeatureARDrone3().setPilotingPCMDYaw(yaw);
    }

    public void startVideo() {
        if (deviceController != null)
            deviceController.getFeatureARDrone3().sendMediaRecordVideoV2(ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_START);
    }

    public void stopVideo() {
        if (deviceController != null)
            deviceController.getFeatureARDrone3().sendMediaRecordVideoV2(ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_STOP);
    }

    public void takeOff() {
        if ((deviceController != null) && (state.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING)))
            deviceController.getFeatureARDrone3().sendPilotingTakeOff();
    }

    public void takePicture() {
        if ((deviceController != null))
            deviceController.getFeatureARDrone3().sendMediaRecordPictureV2();
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

    private void notifyAltitudeChanged(double altitude) {
        for (Listener listener : listeners)
            listener.onAltitudeChanged(altitude);
    }

    private void notifyAxisForCalibrationChanged(ARCOMMANDS_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONAXISTOCALIBRATECHANGED_AXIS_ENUM axis) {
        for (Listener listener : listeners)
            listener.onAxisForCalibrationChanged(axis);
    }

    private void notifyBatteryChargeChanged(int charge) {
        for (Listener listener : listeners)
            listener.onBatteryChargeChanged(charge);
    }

    private void notifyCameraSettingsChanged(float fov, float panMax, float panMin, float tiltMax, float tiltMin) {
        for (Listener listener : listeners)
            listener.onCameraSettingsChanged(fov, panMax, panMin, tiltMax, tiltMin);
    }

    private void notifyCodecConfigured(ARControllerCodec codec) {
        for (Listener listener : listeners)
            listener.onCodecConfigured(codec);
    }

    private void notifyDownloadCompleted(String mediaName) {
        for (Listener listener : listeners)
            listener.onDownloadCompleted(mediaName);
    }

    private void notifyDownloadProgressed(String mediaName, int progress) {
        for (Listener listener : listeners)
            listener.onDownloadProgressed(mediaName, progress);
    }

    private void notifyDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
        for (Listener listener : listeners)
            listener.onDroneConnectionChanged(state);
    }

    private void notifyFrameReceived(ARFrame frame) {
        for (Listener listener : listeners)
            listener.onFrameReceived(frame);
    }

    private void notifyGPSFixedStateChanged(byte fixed) {
        for (Listener listener : listeners)
            listener.onGPSFixedStateChanged(fixed);
    }

    private void notifyHomeTypeAvailabilityChanged(ARCOMMANDS_ARDRONE3_GPSSTATE_HOMETYPEAVAILABILITYCHANGED_TYPE_ENUM type, byte available) {
        for (Listener listener : listeners)
            listener.onHomeTypeAvailabilityChanged(type, available);
    }

    private void notifyHomeTypeChosenChanged(ARCOMMANDS_ARDRONE3_GPSSTATE_HOMETYPECHOSENCHANGED_TYPE_ENUM type) {
        for (Listener listener : listeners)
            listener.onHomeTypeChosenChanged(type);
    }

    private void notifyHorizonChanged(float roll, float pitch, float yaw) {
        for (Listener listener : listeners) {
            listener.onHorizonChanged(roll, pitch, yaw);
        }
    }

    private void notifyMatchingMediasFound(int nbMedias) {
        for (Listener listener : listeners)
            listener.onMatchingMediasFound(nbMedias);
    }

    private void notifyMaxRotationSpeedChanged(float current, float min, float max) {
        for (Listener listener : listeners)
            listener.onMaxRotationSpeedChanged(current, min, max);
    }

    private void notifyNavigateHomeStateChanged(ARCOMMANDS_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_STATE_ENUM state,
                                                ARCOMMANDS_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_REASON_ENUM reason) {
        for (Listener listener : listeners)
            listener.onNavigateHomeStateChanged(state, reason);
    }

    private void notifyPictureTaken(ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error) {
        for (Listener listener : listeners)
            listener.onPictureTaken(error);
    }

    private void notifyPilotingStateChanged(ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state) {
        for (Listener listener : listeners)
            listener.onPilotingStateChanged(state);
    }

    private void notifyResetHomeChanged(double latitude, double longitude, double altitude) {
        for (Listener listener : listeners)
            listener.onResetHomeChanged(latitude, longitude, altitude);
    }

    private void notifySpeedChanged(double speed) {
        for (Listener listener : listeners) {
            listener.onSpeedChanged(speed);
        }
    }

    private void notifyVideoStateChanged(ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE_ENUM state) {
        for (Listener listener : listeners)
            listener.onVideoStateChanged(state);
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
                    notifyDownloadCompleted(mediaName);
                }
            });
        }
    };

    private final ARDeviceControllerListener deviceControllerListener = new ARDeviceControllerListener() {
        @Override
        public void onStateChanged(ARDeviceController deviceController, ARCONTROLLER_DEVICE_STATE_ENUM newState, ARCONTROLLER_ERROR_ENUM error) {
            state = newState;
            if (ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(state))
                Bebop2Drone.this.deviceController.getFeatureARDrone3().sendMediaStreamingVideoEnable((byte) 1);
            else if (ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED.equals(state))
                sdCardModule.cancelGetFlightMedias();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    notifyDroneConnectionChanged(state);
                }
            });
        }

        @Override
        public void onExtensionStateChanged(ARDeviceController deviceController, ARCONTROLLER_DEVICE_STATE_ENUM newState, ARDISCOVERY_PRODUCT_ENUM product, String name, ARCONTROLLER_ERROR_ENUM error) {

        }

        @Override
        public void onCommandReceived(ARDeviceController deviceController, ARCONTROLLER_DICTIONARY_KEY_ENUM commandKey, ARControllerDictionary elementDictionary) {
            // onAltitudeChanged
            if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ALTITUDECHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final double altitude = (double) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ALTITUDECHANGED_ALTITUDE);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyAltitudeChanged(altitude);
                        }
                    });
                }
            }
            // onBatteryChanged
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final int charge = (Integer) args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED_PERCENT);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyBatteryChargeChanged(charge);
                        }
                    });
                }
            }
            // onHorizonChanged
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ATTITUDECHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final float roll = (float) ((Double) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ATTITUDECHANGED_ROLL)).doubleValue();
                    final float pitch = (float)((Double)args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ATTITUDECHANGED_PITCH)).doubleValue();
                    final float yaw = (float)((Double)args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ATTITUDECHANGED_YAW)).doubleValue();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyHorizonChanged(roll, pitch, yaw);
                        }
                    });
                }
            }
            // onPictureTaken
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error = ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM.getFromValue((Integer) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR));
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyPictureTaken(error);
                        }
                    });
                }
            }
            // onPilotingStateChanged
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
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
            // onRunIDChanged
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_RUNSTATE_RUNIDCHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final String runID = (String) args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_RUNSTATE_RUNIDCHANGED_RUNID);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            currentRunId = runID;
                        }
                    });
                }
            }
            // onSpeedChanged
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_SPEEDCHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    float speedX = (float) ((Double) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_SPEEDCHANGED_SPEEDX)).doubleValue();
                    float speedY = (float) ((Double) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_SPEEDCHANGED_SPEEDY)).doubleValue();
                    float speedZ = (float) ((Double) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_SPEEDCHANGED_SPEEDZ)).doubleValue();
                    final double speed = Math.sqrt(Math.pow(speedX, 2) + Math.pow(speedY, 2) + Math.pow(speedZ, 2));
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifySpeedChanged(speed);
                        }
                    });
                }
            }
            // onVideoStateChanged
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE_ENUM state = ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE_ENUM.getFromValue((Integer) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE));
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyVideoStateChanged(state);
                        }
                    });
                }
            }
            // onGPSFixedStateChanged
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_GPSSETTINGSSTATE_GPSFIXSTATECHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final byte fixed = (byte) ((Integer) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_GPSSETTINGSSTATE_GPSFIXSTATECHANGED_FIXED)).intValue();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyGPSFixedStateChanged(fixed);
                        }
                    });
                }
            }
            // onAxisForCalibrationChanged
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONAXISTOCALIBRATECHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final ARCOMMANDS_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONAXISTOCALIBRATECHANGED_AXIS_ENUM axis = ARCOMMANDS_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONAXISTOCALIBRATECHANGED_AXIS_ENUM.getFromValue((Integer) args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONAXISTOCALIBRATECHANGED_AXIS));
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyAxisForCalibrationChanged(axis);
                        }
                    });
                }
            }
            // onNavigateHomeStateChanged
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final ARCOMMANDS_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_STATE_ENUM state = ARCOMMANDS_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_STATE_ENUM.getFromValue((Integer) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_STATE));
                    final ARCOMMANDS_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_REASON_ENUM reason = ARCOMMANDS_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_REASON_ENUM.getFromValue((Integer) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_REASON));
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyNavigateHomeStateChanged(state, reason);
                        }
                    });
                }
            }
            // onHomeTypeChosenChanged
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_GPSSTATE_HOMETYPECHOSENCHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final ARCOMMANDS_ARDRONE3_GPSSTATE_HOMETYPECHOSENCHANGED_TYPE_ENUM type = ARCOMMANDS_ARDRONE3_GPSSTATE_HOMETYPECHOSENCHANGED_TYPE_ENUM.getFromValue((Integer) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_GPSSTATE_HOMETYPECHOSENCHANGED_TYPE));
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyHomeTypeChosenChanged(type);
                        }
                    });
                }
            }
            // onHomeTypeAvailabilityChanged
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_GPSSTATE_HOMETYPEAVAILABILITYCHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final ARCOMMANDS_ARDRONE3_GPSSTATE_HOMETYPEAVAILABILITYCHANGED_TYPE_ENUM type = ARCOMMANDS_ARDRONE3_GPSSTATE_HOMETYPEAVAILABILITYCHANGED_TYPE_ENUM.getFromValue((Integer) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_GPSSTATE_HOMETYPEAVAILABILITYCHANGED_TYPE));
                    final byte available = (byte) ((Integer) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_GPSSTATE_HOMETYPEAVAILABILITYCHANGED_AVAILABLE)).intValue();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyHomeTypeAvailabilityChanged(type, available);
                        }
                    });
                }
            }
            // onResetHomeChanged
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_GPSSETTINGSSTATE_RESETHOMECHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final double latitude = (double) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_GPSSETTINGSSTATE_RESETHOMECHANGED_LATITUDE);
                    final double longitude = (double) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_GPSSETTINGSSTATE_RESETHOMECHANGED_LONGITUDE);
                    final double altitude = (double) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_GPSSETTINGSSTATE_RESETHOMECHANGED_ALTITUDE);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyResetHomeChanged(latitude, longitude, altitude);
                        }
                    });
                }
            }
            // onCameraSettingsChanged
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_CAMERASETTINGSSTATE_CAMERASETTINGSCHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final float fov = (float) ((Double) args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_CAMERASETTINGSSTATE_CAMERASETTINGSCHANGED_FOV)).doubleValue();
                    final float panMax = (float) ((Double) args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_CAMERASETTINGSSTATE_CAMERASETTINGSCHANGED_PANMAX)).doubleValue();
                    final float panMin = (float) ((Double) args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_CAMERASETTINGSSTATE_CAMERASETTINGSCHANGED_PANMIN)).doubleValue();
                    final float tiltMax = (float) ((Double) args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_CAMERASETTINGSSTATE_CAMERASETTINGSCHANGED_TILTMAX)).doubleValue();
                    final float tiltMin = (float) ((Double) args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_CAMERASETTINGSSTATE_CAMERASETTINGSCHANGED_TILTMIN)).doubleValue();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyCameraSettingsChanged(fov, panMax, panMin, tiltMax, tiltMin);
                        }
                    });
                }
            }
            // onMaxRotationSpeedChanged
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_SPEEDSETTINGSSTATE_MAXROTATIONSPEEDCHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final float current = (float) ((Double) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_SPEEDSETTINGSSTATE_MAXROTATIONSPEEDCHANGED_CURRENT)).doubleValue();
                    final float min = (float) ((Double) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_SPEEDSETTINGSSTATE_MAXROTATIONSPEEDCHANGED_MIN)).doubleValue();
                    final float max = (float) ((Double) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_SPEEDSETTINGSSTATE_MAXROTATIONSPEEDCHANGED_MAX)).doubleValue();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyMaxRotationSpeedChanged(current, min, max);
                        }
                    });
                }
            }
        }
    };

    private final ARDeviceControllerStreamListener streamListener = new ARDeviceControllerStreamListener() {
        @Override
        public ARCONTROLLER_ERROR_ENUM configureDecoder(ARDeviceController deviceController, final ARControllerCodec codec) {
            notifyCodecConfigured(codec);
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
