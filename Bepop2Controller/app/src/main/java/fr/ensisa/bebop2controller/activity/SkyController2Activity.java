package fr.ensisa.bebop2controller.activity;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ContextThemeWrapper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.RotateAnimation;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARFrame;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;

import fr.ensisa.bebop2controller.R;
import fr.ensisa.bebop2controller.drone.SkyController2Drone;
import fr.ensisa.bebop2controller.view.Bebop2VideoView;

public class SkyController2Activity extends AppCompatActivity {

    private static final String TAG = "SkyController2Activity";

    private static final int STEP_LINEAR_SPEED = 1, MIN_LINEAR_SPEED = 20, MAX_LINEAR_SPEED = 100;
    private static final int STEP_ROTATION_SPEED = 1, MIN_ROTATION_SPEED = 20, MAX_ROTATION_SPEED = 200;
    private static final int STEP_ANGLE = 1, MIN_ANGLE = 90, MAX_ANGLE = 360;
    private static final int TIME = 18;
    private static final float HORIZON_ADJUSTMENT = 56;

    private SkyController2Drone skyController2Drone;

    private boolean autoRecord = true;
    private boolean isFlipping = false;
    private boolean panoramaPhotos = true;
    private boolean panoramaStarted = false;
    private boolean panoramaVideo = true;

    private int count = 0;
    private int currentDownloadIndex, nbMaxDownload;
    private float currentHorizon = 0;
    private float linearSpeed = 20, rotationSpeed = 20;
    private double panoramaAngle = 360;
    private Chronometer chrono;

    private Bebop2VideoView videoView;
    private FloatingActionButton backFlipBt, downloadBt,
            flipBt, frontFlipBt, leftFlipBt, rightFlipBt;
    private ImageView droneBatteryIconView, controllerBatteryIconView, horizonImageView;
    private ProgressBar loadingAnimation;
    private ProgressDialog connectionProgressDialog;
    private ProgressDialog downloadProgressDialog;
    private TextView droneBatteryTextView, controllerBatteryTextView,
            altitudeTextView, speedTextView, linearSpeedText, rotationSpeedText,
            panoramaAngleText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skycontroller2);

        if (savedInstanceState == null) {
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                autoRecord = extras.getBoolean("autoRecord");
                linearSpeed = extras.getFloat("linearSpeed");
                panoramaAngle = extras.getDouble("panoramaAngle");
                panoramaPhotos = extras.getBoolean("panoramaPhotos");
                panoramaVideo = extras.getBoolean("panoramaVideo");
                rotationSpeed = extras.getFloat("rotationSpeed");
            }
        } else {
            autoRecord = (boolean) savedInstanceState.getSerializable("autoRecord");
            linearSpeed = (float) savedInstanceState.getSerializable("linearSpeed");
            panoramaAngle = (double) savedInstanceState.getSerializable("panoramaAngle");
            panoramaPhotos = (boolean) savedInstanceState.getSerializable("panoramaPhotos");
            panoramaVideo = (boolean) savedInstanceState.getSerializable("panoramaVideo");
            rotationSpeed = (float) savedInstanceState.getSerializable("rotationSpeed");
        }

        initIHM();

        ARDiscoveryDeviceService service = getIntent().getParcelableExtra(MainActivity.EXTRA_DEVICE_SERVICE);
        skyController2Drone = new SkyController2Drone(this, service);
        skyController2Drone.addListener(skyController2Listener);
        skyController2Drone.setLinearSpeed(linearSpeed);
        skyController2Drone.setRotationSpeed(rotationSpeed);
        skyController2Drone.setGPSHomeType(ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_ENUM.ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_TAKEOFF);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if ((skyController2Drone != null) &&
                !(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(skyController2Drone.getControllerConnectionState()))) {
            connectionProgressDialog = new ProgressDialog(this);
            connectionProgressDialog.setIndeterminate(true);
            connectionProgressDialog.setMessage(SkyController2Activity.this.getString(R.string.connecting));
            connectionProgressDialog.setCancelable(false);
            connectionProgressDialog.show();

            if (!skyController2Drone.connect())
                finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (skyController2Drone != null) {
            connectionProgressDialog = new ProgressDialog(this);
            connectionProgressDialog.setIndeterminate(true);
            connectionProgressDialog.setMessage(SkyController2Activity.this.getString(R.string.disconnecting));
            connectionProgressDialog.setCancelable(false);
            connectionProgressDialog.show();

            if (!skyController2Drone.disconnect())
                finish();
        } else
                finish();
    }

    @Override
    public void onDestroy() {
        skyController2Drone.dispose();
        super.onDestroy();
    }

    private void initIHM() {
        videoView = (Bebop2VideoView) findViewById(R.id.videoView);

        droneBatteryIconView = (ImageView) findViewById(R.id.droneBatteryIconView);
        controllerBatteryIconView = (ImageView) findViewById(R.id.controllerBatteryIconView);
        horizonImageView = (ImageView) findViewById(R.id.horizonImageView);

        controllerBatteryTextView = (TextView) findViewById(R.id.controllerBatteryTextView);
        droneBatteryTextView = (TextView) findViewById(R.id.droneBatteryTextView);
        altitudeTextView = (TextView) findViewById(R.id.altitudeTextView);
        speedTextView = (TextView) findViewById(R.id.speedTextView);

        findViewById(R.id.emergencyButton).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                skyController2Drone.emergency();
            }
        });

        downloadBt = (FloatingActionButton) findViewById(R.id.downloadButton);
        downloadBt.setEnabled(false);
        downloadBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                skyController2Drone.getLastFlightMedias();

                downloadProgressDialog = new ProgressDialog(SkyController2Activity.this);
                downloadProgressDialog.setIndeterminate(true);
                downloadProgressDialog.setMessage(SkyController2Activity.this.getString(R.string.fetching_medias));
                downloadProgressDialog.setCancelable(false);
                downloadProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, SkyController2Activity.this.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        skyController2Drone.cancelGetLastFlightMedias();
                    }
                });
                downloadProgressDialog.show();
            }
        });

        backFlipBt = (FloatingActionButton) findViewById(R.id.backFlipButton);
        backFlipBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                skyController2Drone.makeAFlip(ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_ENUM.ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_BACK);
            }
        });

        frontFlipBt = (FloatingActionButton) findViewById(R.id.frontFlipButton);
        frontFlipBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                skyController2Drone.makeAFlip(ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_ENUM.ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_FRONT);
            }
        });

        leftFlipBt = (FloatingActionButton) findViewById(R.id.leftFlipButton);
        leftFlipBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                skyController2Drone.makeAFlip(ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_ENUM.ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_LEFT);
            }
        });

        rightFlipBt = (FloatingActionButton) findViewById(R.id.rightFlipButton);
        rightFlipBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                skyController2Drone.makeAFlip(ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_ENUM.ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_RIGHT);
            }
        });

        flipBt = (FloatingActionButton) findViewById(R.id.flipButton);
        flipBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isFlipping) {
                    frontFlipBt.setVisibility(View.VISIBLE);
                    backFlipBt.setVisibility(View.VISIBLE);
                    leftFlipBt.setVisibility(View.VISIBLE);
                    rightFlipBt.setVisibility(View.VISIBLE);
                    flipBt.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_cancel));
                } else {
                    frontFlipBt.setVisibility(View.INVISIBLE);
                    backFlipBt.setVisibility(View.INVISIBLE);
                    leftFlipBt.setVisibility(View.INVISIBLE);
                    rightFlipBt.setVisibility(View.INVISIBLE);
                    flipBt.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_flip));
                }
                isFlipping = !isFlipping;
            }
        });

        findViewById(R.id.panoramaButton).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                count = 0;
                panoramaStarted = true;
                skyController2Drone.setRotationSpeed((float) 20);

                skyController2Drone.setFlag((byte) 0);
                if (panoramaVideo) {
                    skyController2Drone.stopVideo();
                    skyController2Drone.startVideo();
                }
                skyController2Drone.setYaw((byte) 100);
                chrono.stop();
                chrono.start();
            }
        });

        findViewById(R.id.paramsButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(new ContextThemeWrapper(SkyController2Activity.this, R.style.AppTheme));
                LayoutInflater inflater = SkyController2Activity.this.getLayoutInflater();
                final View dialogView = inflater.inflate(R.layout.drone_options, null);
                dialogBuilder.setView(dialogView);

                final Switch autoRecordSwitch = (Switch) dialogView.findViewById(R.id.autoRecordSwitch);
                final Switch panoramaPhotosSwitch = (Switch) dialogView.findViewById(R.id.panoramaPhotosSwitch);
                final Switch panoramaVideoSwitch = (Switch) dialogView.findViewById(R.id.panoramaVideoSwitch);

                autoRecordSwitch.setChecked(autoRecord);
                panoramaPhotosSwitch.setChecked(panoramaPhotos);
                panoramaVideoSwitch.setChecked(panoramaVideo);

                dialogBuilder.setPositiveButton(R.string.validate, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        autoRecord = autoRecordSwitch.isChecked();
                        panoramaPhotos = panoramaPhotosSwitch.isChecked();
                        panoramaVideo = panoramaVideoSwitch.isChecked();

                        skyController2Drone.setLinearSpeed(linearSpeed);
                        skyController2Drone.setRotationSpeed(rotationSpeed);
                    }
                });

                SeekBar linearSpeedBar = (SeekBar) dialogView.findViewById(R.id.linearSpeedBar);
                SeekBar rotationSpeedBar = (SeekBar) dialogView.findViewById(R.id.rotationSpeedBar);
                SeekBar panoramaAngleBar = (SeekBar) dialogView.findViewById(R.id.panoramaAngleBar);

                linearSpeedText = (TextView) dialogView.findViewById(R.id.linearSpeedText);
                rotationSpeedText = (TextView) dialogView.findViewById(R.id.rotationSpeedText);
                panoramaAngleText = (TextView) dialogView.findViewById(R.id.panoramaAngleText);

                linearSpeedText.setText(getString(R.string.percentage, (int) linearSpeed));
                rotationSpeedText.setText(getString(R.string.percentage, (int) rotationSpeed));
                panoramaAngleText.setText(getString(R.string.angle, (int) panoramaAngle));

                linearSpeedBar.setMax((MAX_LINEAR_SPEED - MIN_LINEAR_SPEED) / STEP_LINEAR_SPEED);
                linearSpeedBar.setProgress((int) linearSpeed - MIN_LINEAR_SPEED);
                linearSpeedBar.setOnSeekBarChangeListener(
                        new SeekBar.OnSeekBarChangeListener() {
                            @Override
                            public void onStopTrackingTouch(SeekBar seekBar) {

                            }

                            @Override
                            public void onStartTrackingTouch(SeekBar seekBar) {

                            }

                            @Override
                            public void onProgressChanged(SeekBar seekBar, int progress,
                                                          boolean fromUser) {
                                linearSpeed = (MIN_LINEAR_SPEED + (progress * STEP_LINEAR_SPEED));
                                linearSpeedText.setText(getString(R.string.percentage, (int) linearSpeed));
                            }
                        }
                );

                rotationSpeedBar.setMax((MAX_ROTATION_SPEED - MIN_ROTATION_SPEED) / STEP_ROTATION_SPEED);
                rotationSpeedBar.setProgress((int) rotationSpeed - MIN_ROTATION_SPEED);
                rotationSpeedBar.setOnSeekBarChangeListener(
                        new SeekBar.OnSeekBarChangeListener() {
                            @Override
                            public void onStopTrackingTouch(SeekBar seekBar) {

                            }

                            @Override
                            public void onStartTrackingTouch(SeekBar seekBar) {

                            }

                            @Override
                            public void onProgressChanged(SeekBar seekBar, int progress,
                                                          boolean fromUser) {
                                rotationSpeed = (MIN_ROTATION_SPEED + (progress * STEP_ROTATION_SPEED));
                                rotationSpeedText.setText(getString(R.string.percentage, (int) rotationSpeed));
                            }
                        }
                );

                panoramaAngleBar.setMax((MAX_ANGLE - MIN_ANGLE) / STEP_ANGLE);
                panoramaAngleBar.setProgress((int) panoramaAngle - MIN_ANGLE);
                panoramaAngleBar.setOnSeekBarChangeListener(
                        new SeekBar.OnSeekBarChangeListener() {
                            @Override
                            public void onStopTrackingTouch(SeekBar seekBar) {

                            }

                            @Override
                            public void onStartTrackingTouch(SeekBar seekBar) {

                            }

                            @Override
                            public void onProgressChanged(SeekBar seekBar, int progress,
                                                          boolean fromUser) {
                                panoramaAngle = (MIN_ANGLE + (progress * STEP_ANGLE));
                                panoramaAngleText.setText(getString(R.string.angle, (int) panoramaAngle));
                            }
                        }
                );

                AlertDialog alertDialog = dialogBuilder.create();
                alertDialog.show();
                alertDialog.getWindow().setBackgroundDrawableResource(R.drawable.style_dialogs);
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.primary_high));
            }
        });

        chrono = (Chronometer) findViewById(R.id.chronometer);
        chrono.setVisibility(View.INVISIBLE);
        chrono.setBase(SystemClock.elapsedRealtime());
        chrono.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
            @Override
            public void onChronometerTick(Chronometer chronometer) {
                int timeRotate = (int) (panoramaAngle * TIME) / 360;

                if (count == timeRotate) {
                    skyController2Drone.setYaw((byte) 0);
                    if (panoramaVideo) {
                        skyController2Drone.stopVideo();
                        if (autoRecord)
                            skyController2Drone.startVideo();
                    }
                    skyController2Drone.setRotationSpeed(rotationSpeed);
                }
                if (panoramaPhotos) {
                    if (count % 2 == 0 && count < timeRotate && panoramaStarted)
                        skyController2Drone.takePicture();
                }
                count++;
            }

        });

        loadingAnimation = (ProgressBar)findViewById(R.id.loadingAnimation);
    }

    @SuppressLint("DefaultLocale")
    private final SkyController2Drone.Listener skyController2Listener = new SkyController2Drone.Listener() {
        @Override
        public void onAltitudeChanged(double altitudeValue) {
            altitudeTextView.setText(String.format(" %.1f m", altitudeValue));
        }

        @Override
        public void onCodecConfigured(ARControllerCodec codec) {
            videoView.configureDecoder(codec);
        }

        @Override
        public void onControllerBatteryChargeChanged(int charge) {
            controllerBatteryTextView.setText(String.format(" %d%%", charge));
            switch(charge / 10) {
                case 10:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_100));
                    break;
                case 9:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_100));
                    break;
                case 8:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_80));
                    break;
                case 7:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_70));
                    break;
                case 6:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_60));
                    break;
                case 5:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_50));
                    break;
                case 4:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_40));
                    break;
                case 3:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_30));
                    break;
                case 2:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_20));
                    break;
                case 1:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_10));
                    break;
                case 0:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_alert));
                    break;
                default:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_alert));
                    break;
            }
        }

        @Override
        public void onControllerConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
            switch (state) {
                case ARCONTROLLER_DEVICE_STATE_RUNNING:
                    connectionProgressDialog.dismiss();
                    if (!ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(skyController2Drone.getDroneConnectionState()))
                        loadingAnimation.setVisibility(View.VISIBLE);
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
        public void onDroneBatteryChargeChanged(int charge) {
            droneBatteryTextView.setText(String.format(" %d%%", charge));
            switch(charge / 10) {
                case 10:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_100));
                    break;
                case 9:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_100));
                    break;
                case 8:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_80));
                    break;
                case 7:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_70));
                    break;
                case 6:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_60));
                    break;
                case 5:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_50));
                    break;
                case 4:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_40));
                    break;
                case 3:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_30));
                    break;
                case 2:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_20));
                    break;
                case 1:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_10));
                    break;
                case 0:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_alert));
                    break;
                default:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_alert));
                    break;
            }
        }

        @Override
        public void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
            switch (state) {
                case ARCONTROLLER_DEVICE_STATE_RUNNING:
                    loadingAnimation.setVisibility(View.GONE);
                    horizonImageView.setVisibility(View.VISIBLE);
                    break;
                default:
                    loadingAnimation.setVisibility(View.VISIBLE);
                    horizonImageView.setVisibility(View.INVISIBLE);
                    break;
            }
        }

        @Override
        public void onFrameReceived(ARFrame frame) {
            videoView.displayFrame(frame);
        }

        @Override
        public void onDownloadComplete(String mediaName) {
            currentDownloadIndex++;
            downloadProgressDialog.setSecondaryProgress(currentDownloadIndex * 100);

            if (currentDownloadIndex > nbMaxDownload) {
                downloadProgressDialog.dismiss();
                downloadProgressDialog = null;
            }
        }

        @Override
        public void onDownloadProgressed(String mediaName, int progress) {
            downloadProgressDialog.setProgress(((currentDownloadIndex - 1) * 100) + progress);
        }

        @Override
        public void onHorizonChanged(float roll) {
            final RotateAnimation rotateAnim = new RotateAnimation(currentHorizon, roll * HORIZON_ADJUSTMENT,
                    RotateAnimation.RELATIVE_TO_SELF, 0.5f,
                    RotateAnimation.RELATIVE_TO_SELF, 0.5f);

            rotateAnim.setDuration(200);
            rotateAnim.setFillAfter(true);
            horizonImageView.startAnimation(rotateAnim);
            currentHorizon = roll * HORIZON_ADJUSTMENT;
        }

        @Override
        public void onMatchingMediasFound(int nbMedias) {
            downloadProgressDialog.dismiss();

            nbMaxDownload = nbMedias;
            currentDownloadIndex = 1;

            if (nbMedias > 0) {
                downloadProgressDialog = new ProgressDialog(SkyController2Activity.this);
                downloadProgressDialog.setIndeterminate(false);
                downloadProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                downloadProgressDialog.setMessage(SkyController2Activity.this.getString(R.string.downloading_medias));
                downloadProgressDialog.setMax(nbMaxDownload * 100);
                downloadProgressDialog.setSecondaryProgress(currentDownloadIndex * 100);
                downloadProgressDialog.setProgress(0);
                downloadProgressDialog.setCancelable(false);
                downloadProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, SkyController2Activity.this.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        skyController2Drone.cancelGetLastFlightMedias();
                    }
                });
                downloadProgressDialog.show();
            }
        }

        @Override
        public void onPictureTaken(ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error) {
            Toast.makeText(getApplicationContext(), R.string.picture_taken, Toast.LENGTH_SHORT).show();
            Log.i(TAG, "Picture has been taken");
        }

        @Override
        public void onPilotingStateChanged(ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state) {
            switch (state) {
                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                    downloadBt.setEnabled(true);
                    break;
                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                    downloadBt.setEnabled(false);
                    break;
                default:
                    downloadBt.setEnabled(false);
            }
        }

        @Override
        public void onSpeedChanged(float speed) {
            speedTextView.setText(String.format("  %.1f m/s", speed));
        }

        @Override
        public void onVideoStateChanged(ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE_ENUM state) {
            switch (state) {
                case ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE_STARTED:
                    Toast.makeText(getApplicationContext(), R.string.video_started, Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "Video started recording");
                case ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE_STOPPED:
                    Toast.makeText(getApplicationContext(), R.string.video_stopped, Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "Video stopped recording");
            }
        }
    };

}
