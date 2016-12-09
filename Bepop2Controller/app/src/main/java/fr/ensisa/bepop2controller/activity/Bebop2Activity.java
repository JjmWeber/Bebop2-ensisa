package fr.ensisa.bepop2controller.activity;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARFrame;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;

import fr.ensisa.bepop2controller.R;
import fr.ensisa.bepop2controller.drone.Bebop2Drone;
import fr.ensisa.bepop2controller.view.Bebop2VideoView;
import io.github.controlwear.virtual.joystick.android.JoystickView;

public class Bebop2Activity extends AppCompatActivity {

    private static final String TAG = "Bebop2Activity";
    private static final double MINIMUM_RADIUS = 20.0;
    private static final float HORIZON_ADJUSTMENT = 56;
    private static final float ROTATION_MAX_SPEED = 36;

    private Bebop2Drone bebop2Drone;

    private ProgressDialog connectionProgressDialog;
    private ProgressDialog downloadProgressDialog;

    private Bebop2VideoView videoView;

    private ImageView batteryIconView;
    private ImageView horizonImageView;

    private TextView batteryTextView;
    private TextView altitudeTextView;
    private TextView speedTextView;

    private Button takeOffAndLandBt;
    private FloatingActionButton downloadBt;
    private FloatingActionButton videoBt;
    private FloatingActionButton frontFlipBt;
    private FloatingActionButton backFlipBt;
    private FloatingActionButton leftFlipBt;
    private FloatingActionButton rightFlipBt;
    private FloatingActionButton flipBt;

    private int nbMaxDownload;
    private int currentDownloadIndex;
    private boolean isRecording = false;
    private boolean isFlipping = false;
    private float currentHorizon = 0;
    private double pitch = 0, roll = 0, gaz = 0, yaw = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bebop2);

        initIHM();

        ARDiscoveryDeviceService service = getIntent().getParcelableExtra(MainActivity.EXTRA_DEVICE_SERVICE);
        bebop2Drone = new Bebop2Drone(this, service);
        bebop2Drone.addListener(bebopListener);
        bebop2Drone.getDeviceController().getFeatureARDrone3().sendGPSSettingsHomeType((ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_ENUM.ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_TAKEOFF));
        bebop2Drone.setFlag((byte) 1);
        bebop2Drone.getDeviceController().getFeatureARDrone3().sendSpeedSettingsMaxRotationSpeed(ROTATION_MAX_SPEED);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if((bebop2Drone != null) && !(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(bebop2Drone.getConnectionState()))) {
            connectionProgressDialog = new ProgressDialog(this);
            connectionProgressDialog.setIndeterminate(true);
            connectionProgressDialog.setMessage(Bebop2Activity.this.getString(R.string.connecting));
            connectionProgressDialog.setCancelable(false);
            connectionProgressDialog.show();

            if(!bebop2Drone.connect())
                finish();
        }
    }

    @Override
    public void onBackPressed() {
        if(bebop2Drone != null) {
            connectionProgressDialog = new ProgressDialog(this);
            connectionProgressDialog.setIndeterminate(true);
            connectionProgressDialog.setMessage(Bebop2Activity.this.getString(R.string.disconnecting));
            connectionProgressDialog.setCancelable(false);
            connectionProgressDialog.show();

            if(!bebop2Drone.disconnect())
                finish();
        }
    }

    @Override
    public void onDestroy() {
        bebop2Drone.dispose();
        super.onDestroy();
    }

    private void initIHM() {
        videoView = (Bebop2VideoView) findViewById(R.id.videoView);

        findViewById(R.id.emergencyButton).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                bebop2Drone.emergency();
            }
        });

        takeOffAndLandBt = (Button) findViewById(R.id.takeOffAndLandButton);
        takeOffAndLandBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                switch (bebop2Drone.getFlyingState()) {
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                        bebop2Drone.takeOff();
                        break;
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                        bebop2Drone.land();
                        break;
                    default:
                }
            }
        });

        findViewById(R.id.cameraButton).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                bebop2Drone.takePicture();
            }
        });

        videoBt = (FloatingActionButton) findViewById(R.id.videoButton);
        videoBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isRecording)
                    bebop2Drone.getDeviceController().getFeatureARDrone3().sendMediaRecordVideoV2(ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_START);
                else
                    bebop2Drone.getDeviceController().getFeatureARDrone3().sendMediaRecordVideoV2(ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_STOP);
            }
        });

        downloadBt = (FloatingActionButton)findViewById(R.id.downloadButton);
        downloadBt.setEnabled(false);
        downloadBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                bebop2Drone.getLastFlightMedias();

                downloadProgressDialog = new ProgressDialog(Bebop2Activity.this);
                downloadProgressDialog.setIndeterminate(true);
                downloadProgressDialog.setMessage(Bebop2Activity.this.getString(R.string.fetching_medias));
                downloadProgressDialog.setCancelable(false);
                downloadProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, Bebop2Activity.this.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        bebop2Drone.cancelGetLastFlightMedias();
                    }
                });
                downloadProgressDialog.show();
            }
        });

        findViewById(R.id.homeButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bebop2Drone.getDeviceController().getFeatureARDrone3().sendPilotingNavigateHome((byte) 1);
            }
        });

        frontFlipBt = (FloatingActionButton) findViewById(R.id.frontFlipButton);
        frontFlipBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bebop2Drone.getDeviceController().getFeatureARDrone3().sendAnimationsFlip((ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_ENUM.ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_FRONT));
            }
        });

        backFlipBt = (FloatingActionButton) findViewById(R.id.backFlipButton);
        backFlipBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bebop2Drone.getDeviceController().getFeatureARDrone3().sendAnimationsFlip((ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_ENUM.ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_BACK));
            }
        });

        leftFlipBt = (FloatingActionButton) findViewById(R.id.leftFlipButton);
        leftFlipBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bebop2Drone.getDeviceController().getFeatureARDrone3().sendAnimationsFlip((ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_ENUM.ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_LEFT));
            }
        });

        rightFlipBt = (FloatingActionButton) findViewById(R.id.rightFlipButton);
        rightFlipBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bebop2Drone.getDeviceController().getFeatureARDrone3().sendAnimationsFlip((ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_ENUM.ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_RIGHT));
            }
        });

        flipBt = (FloatingActionButton) findViewById(R.id.flipButton);
        flipBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isFlipping) {
                    frontFlipBt.setVisibility(View.VISIBLE);
                    backFlipBt.setVisibility(View.VISIBLE);
                    leftFlipBt.setVisibility(View.VISIBLE);
                    rightFlipBt.setVisibility(View.VISIBLE);
                    flipBt.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_cancel));
                } else {
                    frontFlipBt.setVisibility(View.INVISIBLE);
                    backFlipBt.setVisibility(View.INVISIBLE);
                    leftFlipBt.setVisibility(View.INVISIBLE);
                    rightFlipBt.setVisibility(View.INVISIBLE);
                    flipBt.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_flip));
                }
                isFlipping = !isFlipping;
            }
        });

        ((JoystickView)findViewById(R.id.leftJoystick)).setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {

                double leftRadius = (Math.PI*(angle%90))/180;
                switch(angle/90) {
                    case 0:
                        pitch =  Math.sin(leftRadius) * strength;
                        roll = Math.cos(leftRadius) * strength;
                        break;
                    case 1:
                        pitch = Math.cos(leftRadius) * strength;
                        roll = -Math.sin(leftRadius) * strength;
                        break;
                    case 2:
                        pitch = -Math.sin(leftRadius) * strength;
                        roll = -Math.cos(leftRadius) * strength;
                        break;
                    case 3:
                        pitch = -Math.cos(leftRadius) * strength;
                        roll = Math.sin(leftRadius) * strength;
                        break;
                    case 4:
                        pitch = 0;
                        roll = strength;
                        break;
                }
                if(pitch < MINIMUM_RADIUS && pitch > -MINIMUM_RADIUS)
                    pitch = 0;
                if(roll < MINIMUM_RADIUS && roll > -MINIMUM_RADIUS)
                    roll = 0;
                bebop2Drone.setFlag((byte) 1);
                bebop2Drone.setPitch((byte) pitch);
                bebop2Drone.setRoll((byte) roll);
            }
        });

        ((JoystickView)findViewById(R.id.rightJoystick)).setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                double rightRadius = (Math.PI*(angle%90))/180;
                switch(angle/90) {
                    case 0:
                        gaz =  Math.sin(rightRadius) * strength;
                        yaw = Math.cos(rightRadius) * strength;
                        break;
                    case 1:
                        gaz = Math.cos(rightRadius) * strength;
                        yaw = -Math.sin(rightRadius) * strength;
                        break;
                    case 2:
                        gaz = -Math.sin(rightRadius) * strength;
                        yaw = -Math.cos(rightRadius) * strength;
                        break;
                    case 3:
                        gaz = -Math.cos(rightRadius) * strength;
                        yaw = Math.sin(rightRadius) * strength;
                        break;
                    case 4:
                        gaz = 0;
                        yaw = strength;
                        break;
                }
                if(gaz < MINIMUM_RADIUS && gaz > -MINIMUM_RADIUS)
                    gaz = 0;
                if(yaw < MINIMUM_RADIUS && yaw > -MINIMUM_RADIUS)
                    yaw = 0;
                bebop2Drone.setGaz((byte) gaz);
                bebop2Drone.setYaw((byte) yaw);
            }
        });

        batteryIconView = (ImageView) findViewById(R.id.batteryIconView);
        horizonImageView = (ImageView) findViewById(R.id.horizonImageView);

        batteryTextView = (TextView) findViewById(R.id.batteryTextView);
        altitudeTextView = (TextView) findViewById(R.id.altitudeTextView);
        speedTextView = (TextView) findViewById(R.id.speedTextView);
    }

    @SuppressLint("DefaultLocale")
    private final Bebop2Drone.Listener bebopListener = new Bebop2Drone.Listener() {
        @Override
        public void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
            switch (state) {
                case ARCONTROLLER_DEVICE_STATE_RUNNING:
                    connectionProgressDialog.dismiss();
                    break;
                case ARCONTROLLER_DEVICE_STATE_STOPPED:
                    connectionProgressDialog.dismiss();
                    finish();
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onBatteryChargeChanged(int batteryPercentage) {
            batteryTextView.setText(String.format(" %d%%", batteryPercentage));
            switch(batteryPercentage / 10) {
                case 10:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_100));
                    break;
                case 9:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_100));
                    break;
                case 8:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_80));
                    break;
                case 7:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_70));
                    break;
                case 6:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_60));
                    break;
                case 5:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_50));
                    break;
                case 4:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_40));
                    break;
                case 3:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_30));
                    break;
                case 2:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_20));
                    break;
                case 1:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_10));
                    break;
                case 0:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_alert));
                    break;
                default:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_alert));
                    break;
            }
        }

        @Override
        public void onSpeedChanged(double speed) {
            speedTextView.setText(String.format("  %.1f m/s", speed));
        }

        @Override
        public void horizonChanged(float roll) {
            final RotateAnimation rotateAnim = new RotateAnimation(currentHorizon, roll*HORIZON_ADJUSTMENT,
                    RotateAnimation.RELATIVE_TO_SELF, 0.5f,
                    RotateAnimation.RELATIVE_TO_SELF, 0.5f);

            rotateAnim.setDuration(200);
            rotateAnim.setFillAfter(true);
            horizonImageView.startAnimation(rotateAnim);
            currentHorizon = roll*HORIZON_ADJUSTMENT;
        }

        @Override
        public void onAltitudeChanged(double altitudeValue) {
            altitudeTextView.setText(String.format(" %.1f m", altitudeValue));
        }

        @Override
        public void onPilotingStateChanged(ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state) {
            switch (state) {
                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                    takeOffAndLandBt.setText(R.string.take_off);
                    takeOffAndLandBt.setEnabled(true);
                    downloadBt.setEnabled(true);
                    break;
                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                    takeOffAndLandBt.setText(R.string.land);
                    takeOffAndLandBt.setEnabled(true);
                    downloadBt.setEnabled(false);
                    break;
                default:
                    takeOffAndLandBt.setEnabled(false);
                    downloadBt.setEnabled(false);
            }
        }

        @Override
        public void onPictureTaken(ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error) {
            Toast.makeText(getApplicationContext(), R.string.picture_taken, Toast.LENGTH_SHORT).show();
            Log.i(TAG, "Picture has been taken");
        }

        @Override
        public void onVideoStateChanged(ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE_ENUM state) {
            switch(state) {
                case ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE_STARTED:
                    videoBt.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_stop_video));
                    Toast.makeText(getApplicationContext(), R.string.video_started, Toast.LENGTH_SHORT).show();
                    isRecording = true;
                    Log.i(TAG, "Video started recording");
                case ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE_STOPPED:
                    videoBt.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_video));
                    Toast.makeText(getApplicationContext(), R.string.video_stopped, Toast.LENGTH_SHORT).show();
                    isRecording = false;
                    Log.i(TAG, "Video stopped recording");
            }
        }

        @Override
        public void configureDecoder(ARControllerCodec codec) {
            videoView.configureDecoder(codec);
        }

        @Override
        public void onFrameReceived(ARFrame frame) {
            videoView.displayFrame(frame);
        }

        @Override
        public void onMatchingMediasFound(int nbMedias) {
            downloadProgressDialog.dismiss();

            nbMaxDownload = nbMedias;
            currentDownloadIndex = 1;

            if(nbMedias > 0) {
                downloadProgressDialog = new ProgressDialog(Bebop2Activity.this);
                downloadProgressDialog.setIndeterminate(false);
                downloadProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                downloadProgressDialog.setMessage(Bebop2Activity.this.getString(R.string.downloading_medias));
                downloadProgressDialog.setMax(nbMaxDownload * 100);
                downloadProgressDialog.setSecondaryProgress(currentDownloadIndex * 100);
                downloadProgressDialog.setProgress(0);
                downloadProgressDialog.setCancelable(false);
                downloadProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, Bebop2Activity.this.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        bebop2Drone.cancelGetLastFlightMedias();
                    }
                });
                downloadProgressDialog.show();
            }
        }

        @Override
        public void onDownloadProgressed(String mediaName, int progress) {
            downloadProgressDialog.setProgress(((currentDownloadIndex - 1) * 100) + progress);
        }

        @Override
        public void onDownloadComplete(String mediaName) {
            currentDownloadIndex++;
            downloadProgressDialog.setSecondaryProgress(currentDownloadIndex * 100);

            if(currentDownloadIndex > nbMaxDownload) {
                downloadProgressDialog.dismiss();
                downloadProgressDialog = null;
            }
        }
    };

}
