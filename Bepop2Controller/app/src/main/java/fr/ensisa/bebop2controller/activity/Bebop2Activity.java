package fr.ensisa.bebop2controller.activity;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AlertDialog.Builder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ContextThemeWrapper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
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
import fr.ensisa.bebop2controller.drone.Bebop2Drone;
import fr.ensisa.bebop2controller.view.Bebop2VideoView;
import io.github.controlwear.virtual.joystick.android.JoystickView;

public class Bebop2Activity extends AppCompatActivity {

    private static final String TAG = "Bebop2Activity";

    private static final int STEP_LINEAR_SPEED = 1, MIN_LINEAR_SPEED = 20, MAX_LINEAR_SPEED = 100;
    private static final int STEP_ROTATION_SPEED = 1, MIN_ROTATION_SPEED = 20, MAX_ROTATION_SPEED = 200;
    private static final int STEP_ANGLE = 1, MIN_ANGLE = 90, MAX_ANGLE = 360;
    private static final int TIME = 18;
    private static final float HORIZON_ADJUSTMENT = 56;
    private static final double MINIMUM_RADIUS = 20.0;

    private Bebop2Drone bebop2Drone;

    private boolean autoRecord = true;
    private boolean commandsInverted = false;
    private boolean isEmergency = false;
    private boolean isFlipping = false;
    private boolean isPassed = false;
    private boolean isRecording = false;
    private boolean joysticksFixed = true;
    private boolean joysticksInverted = false;
    private boolean panoramaPhotos = true;
    private boolean panoramaStarted = false;
    private boolean panoramaVideo = true;

    private int count = 0;
    private int currentDownloadIndex, nbMaxDownload;
    private int[] margins;
    private float currentHorizon = 0;
    private float linearSpeed = 20, rotationSpeed = 20;
    private double gaz = 0, pitch = 0, roll = 0, yaw = 0;
    private double panoramaAngle = 360;
    private Chronometer chrono;

    private Bebop2VideoView videoView;
    private Button takeOffAndLandBt;
    private FloatingActionButton backFlipBt, downloadBt, emergencyBt,
            flipBt, frontFlipBt, leftFlipBt, rightFlipBt, videoBt;
    private ImageView batteryIconView, horizonImageView, leftJoystickUpView,
            leftJoystickDownView, leftJoystickLeftView, leftJoystickRightView,
            rightJoystickUpView, rightJoystickDownView, rightJoystickLeftView,
            rightJoystickRightView;
    private ProgressDialog connectionProgressDialog, downloadProgressDialog;
    private TextView altitudeTextView, batteryTextView, speedTextView,
            linearSpeedText, rotationSpeedText, panoramaAngleText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bebop2);

        if (savedInstanceState == null) {
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                autoRecord = extras.getBoolean("autoRecord");
                commandsInverted = extras.getBoolean("commandsInverted");
                joysticksFixed = extras.getBoolean("joysticksFixed");
                joysticksInverted = extras.getBoolean("joysticksInverted");
                linearSpeed = extras.getFloat("linearSpeed");
                panoramaAngle = extras.getDouble("panoramaAngle");
                panoramaPhotos = extras.getBoolean("panoramaPhotos");
                panoramaVideo = extras.getBoolean("panoramaVideo");
                rotationSpeed = extras.getFloat("rotationSpeed");
            }
        } else {
            autoRecord = (boolean) savedInstanceState.getSerializable("autoRecord");
            commandsInverted = (boolean) savedInstanceState.getSerializable("commandsInverted");
            joysticksFixed = (boolean) savedInstanceState.getSerializable("joysticksFixed");
            joysticksInverted = (boolean) savedInstanceState.getSerializable("joysticksInverted");
            linearSpeed = (float) savedInstanceState.getSerializable("linearSpeed");
            panoramaAngle = (double) savedInstanceState.getSerializable("panoramaAngle");
            panoramaPhotos = (boolean) savedInstanceState.getSerializable("panoramaPhotos");
            panoramaVideo = (boolean) savedInstanceState.getSerializable("panoramaVideo");
            rotationSpeed = (float) savedInstanceState.getSerializable("rotationSpeed");
        }

        margins = new int[] {getResources().getDimensionPixelSize(R.dimen.null_margin),
                getResources().getDimensionPixelSize(R.dimen.left_low_margin),
                getResources().getDimensionPixelSize(R.dimen.right_low_margin),
                getResources().getDimensionPixelSize(R.dimen.left_medium_margin),
                getResources().getDimensionPixelSize(R.dimen.right_medium_margin),
                getResources().getDimensionPixelSize(R.dimen.left_high_margin),
                getResources().getDimensionPixelSize(R.dimen.right_high_margin)};

        initIHM();

        ARDiscoveryDeviceService service = getIntent().getParcelableExtra(MainActivity.EXTRA_DEVICE_SERVICE);
        bebop2Drone = new Bebop2Drone(this, service);
        bebop2Drone.addListener(bebopListener);
        bebop2Drone.setLinearSpeed(linearSpeed);
        bebop2Drone.setRotationSpeed(rotationSpeed);
        bebop2Drone.setGPSHomeType(ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_ENUM.ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_TAKEOFF);
        bebop2Drone.setFlag((byte) 1);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if ((bebop2Drone != null) && !(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(bebop2Drone.getConnectionState()))) {
            connectionProgressDialog = new ProgressDialog(this);
            connectionProgressDialog.setIndeterminate(true);
            connectionProgressDialog.setMessage(Bebop2Activity.this.getString(R.string.connecting));
            connectionProgressDialog.setCancelable(false);
            connectionProgressDialog.show();

            if (!bebop2Drone.connect())
                finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (bebop2Drone != null) {
            connectionProgressDialog = new ProgressDialog(this);
            connectionProgressDialog.setIndeterminate(true);
            connectionProgressDialog.setMessage(Bebop2Activity.this.getString(R.string.disconnecting));
            connectionProgressDialog.setCancelable(false);
            connectionProgressDialog.show();

            if (!bebop2Drone.disconnect())
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

        batteryIconView = (ImageView) findViewById(R.id.batteryIconView);
        horizonImageView = (ImageView) findViewById(R.id.horizonImageView);
        leftJoystickUpView = (ImageView) findViewById(R.id.leftJoystickUpView);
        leftJoystickDownView = (ImageView) findViewById(R.id.leftJoystickDownView);
        leftJoystickLeftView = (ImageView) findViewById(R.id.leftJoystickLeftView);
        leftJoystickRightView = (ImageView) findViewById(R.id.leftJoystickRightView);
        rightJoystickUpView = (ImageView) findViewById(R.id.rightJoystickUpView);
        rightJoystickDownView = (ImageView) findViewById(R.id.rightJoystickDownView);
        rightJoystickLeftView = (ImageView) findViewById(R.id.rightJoystickLeftView);
        rightJoystickRightView = (ImageView) findViewById(R.id.rightJoystickRightView);

        if(joysticksInverted) {
            leftJoystickUpView.setImageResource(commandsInverted ? R.drawable.ic_downward : R.drawable.ic_upward);
            leftJoystickDownView.setImageResource(commandsInverted ? R.drawable.ic_upward : R.drawable.ic_downward);
            leftJoystickLeftView.setImageResource(R.drawable.ic_left_rotation);
            ((ViewGroup.MarginLayoutParams) leftJoystickLeftView.getLayoutParams()).setMargins(margins[2], margins[0], margins[0], margins[4]);
            leftJoystickRightView.setImageResource(R.drawable.ic_right_rotation);
            ((ViewGroup.MarginLayoutParams) leftJoystickRightView.getLayoutParams()).setMargins(margins[6], margins[0], margins[0], margins[4]);
            rightJoystickUpView.setImageResource(R.drawable.ic_forward);
            rightJoystickDownView.setImageResource(R.drawable.ic_backward);
            rightJoystickLeftView.setImageResource(R.drawable.ic_left);
            ((ViewGroup.MarginLayoutParams) rightJoystickLeftView.getLayoutParams()).setMargins(margins[0], margins[0], margins[5], margins[3]);
            rightJoystickRightView.setImageResource(R.drawable.ic_right);
            ((ViewGroup.MarginLayoutParams) rightJoystickRightView.getLayoutParams()).setMargins(margins[0], margins[0], margins[1], margins[3]);
        } else {
            leftJoystickUpView.setImageResource(R.drawable.ic_forward);
            leftJoystickDownView.setImageResource(R.drawable.ic_backward);
            leftJoystickLeftView.setImageResource(R.drawable.ic_left);
            ((ViewGroup.MarginLayoutParams) leftJoystickLeftView.getLayoutParams()).setMargins(margins[1], margins[0], margins[0], margins[3]);
            leftJoystickRightView.setImageResource(R.drawable.ic_right);
            ((ViewGroup.MarginLayoutParams) leftJoystickRightView.getLayoutParams()).setMargins(margins[5], margins[0], margins[0], margins[3]);
            rightJoystickUpView.setImageResource(commandsInverted ? R.drawable.ic_downward : R.drawable.ic_upward);
            rightJoystickDownView.setImageResource(commandsInverted ? R.drawable.ic_upward : R.drawable.ic_downward);
            rightJoystickLeftView.setImageResource(R.drawable.ic_left_rotation);
            ((ViewGroup.MarginLayoutParams) rightJoystickLeftView.getLayoutParams()).setMargins(margins[0], margins[0], margins[6], margins[4]);
            rightJoystickRightView.setImageResource(R.drawable.ic_right_rotation);
            ((ViewGroup.MarginLayoutParams) rightJoystickRightView.getLayoutParams()).setMargins(margins[0], margins[0], margins[2], margins[4]);
        }

        batteryTextView = (TextView) findViewById(R.id.batteryTextView);
        altitudeTextView = (TextView) findViewById(R.id.altitudeTextView);
        speedTextView = (TextView) findViewById(R.id.speedTextView);

        final JoystickView leftJoystick = (JoystickView) findViewById(R.id.leftJoystick);
        leftJoystick.setFixedCenter(joysticksFixed);
        leftJoystick.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    leftJoystickUpView.setVisibility(View.INVISIBLE);
                    leftJoystickDownView.setVisibility(View.INVISIBLE);
                    leftJoystickLeftView.setVisibility(View.INVISIBLE);
                    leftJoystickRightView.setVisibility(View.INVISIBLE);
                }
                leftJoystick.onTouchEvent(event);
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    leftJoystickUpView.setVisibility(View.VISIBLE);
                    leftJoystickDownView.setVisibility(View.VISIBLE);
                    leftJoystickLeftView.setVisibility(View.VISIBLE);
                    leftJoystickRightView.setVisibility(View.VISIBLE);
                    if(!joysticksFixed) {
                        leftJoystick.setFixedCenter(true);
                        leftJoystick.setFixedCenter(false);
                    }
                }
                return true;
            }
        });
        leftJoystick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                double leftRadius = (Math.PI * (angle % 90)) / 180;
                switch (angle / 90) {
                    case 0:
                        pitch = Math.sin(leftRadius) * strength;
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
                if (pitch < MINIMUM_RADIUS && pitch > -MINIMUM_RADIUS)
                    pitch = 0;
                if (roll < MINIMUM_RADIUS && roll > -MINIMUM_RADIUS)
                    roll = 0;

                if (!joysticksInverted) {
                    if (roll == 0 && pitch == 0 && !isPassed) {
                        bebop2Drone.setFlag((byte) 0);
                        isPassed = true;
                    } else {
                        bebop2Drone.setFlag((byte) 1);
                        bebop2Drone.setPitch((byte) pitch);
                        bebop2Drone.setRoll((byte) roll);
                        isPassed = false;
                    }
                } else {
                    pitch = (commandsInverted ? -pitch : pitch);
                    bebop2Drone.setGaz((byte) pitch);
                    bebop2Drone.setYaw((byte) roll);
                }
            }
        });

        final JoystickView rightJoystick = (JoystickView) findViewById(R.id.rightJoystick);
        rightJoystick.setFixedCenter(joysticksFixed);
        rightJoystick.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    rightJoystickUpView.setVisibility(View.INVISIBLE);
                    rightJoystickDownView.setVisibility(View.INVISIBLE);
                    rightJoystickLeftView.setVisibility(View.INVISIBLE);
                    rightJoystickRightView.setVisibility(View.INVISIBLE);
                }
                rightJoystick.onTouchEvent(event);
                if(event.getAction() == MotionEvent.ACTION_UP) {
                    rightJoystickUpView.setVisibility(View.VISIBLE);
                    rightJoystickDownView.setVisibility(View.VISIBLE);
                    rightJoystickLeftView.setVisibility(View.VISIBLE);
                    rightJoystickRightView.setVisibility(View.VISIBLE);
                    if(!joysticksFixed) {
                        rightJoystick.setFixedCenter(true);
                        rightJoystick.setFixedCenter(false);
                    }
                }
                return true;
            }
        });
        rightJoystick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                double rightRadius = (Math.PI * (angle % 90)) / 180;
                switch (angle / 90) {
                    case 0:
                        gaz = Math.sin(rightRadius) * strength;
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
                if (gaz < MINIMUM_RADIUS && gaz > -MINIMUM_RADIUS)
                    gaz = 0;
                if (yaw < MINIMUM_RADIUS && yaw > -MINIMUM_RADIUS)
                    yaw = 0;

                if (!joysticksInverted) {
                    gaz = (commandsInverted ? -gaz : gaz);
                    bebop2Drone.setGaz((byte) gaz);
                    bebop2Drone.setYaw((byte) yaw);
                } else {
                    if (gaz == 0 && yaw == 0 && !isPassed) {
                        bebop2Drone.setFlag((byte) 0);
                        isPassed = true;
                    } else {
                        bebop2Drone.setFlag((byte) 1);
                        bebop2Drone.setPitch((byte) gaz);
                        bebop2Drone.setRoll((byte) yaw);
                        isPassed = false;
                    }
                }
            }
        });

        takeOffAndLandBt = (Button) findViewById(R.id.takeOffAndLandButton);
        takeOffAndLandBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                switch (bebop2Drone.getFlyingState()) {
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                        bebop2Drone.doAFlatTrim();
                        bebop2Drone.setAutoRecordMode(autoRecord);
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

        emergencyBt = (FloatingActionButton) findViewById(R.id.emergencyButton);
        emergencyBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(isEmergency) {
                    bebop2Drone.emergency();
                    emergencyBt.setImageResource(R.drawable.ic_lock);
                } else
                    Toast.makeText(getApplicationContext(), R.string.hold_on, Toast.LENGTH_SHORT).show();
            }
        });
        emergencyBt.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                emergencyBt.setImageResource(isEmergency ? R.drawable.ic_lock : R.drawable.ic_emergency);
                isEmergency = !isEmergency;
                return true;
            }
        });

        findViewById(R.id.homeButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bebop2Drone.setFlag((byte) 0);
                bebop2Drone.setGPSHomeType(ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_ENUM.ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_TAKEOFF);
                bebop2Drone.goHome();
            }
        });

        downloadBt = (FloatingActionButton) findViewById(R.id.downloadButton);
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

        backFlipBt = (FloatingActionButton) findViewById(R.id.backFlipButton);
        backFlipBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bebop2Drone.makeAFlip(ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_ENUM.ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_BACK);
            }
        });

        frontFlipBt = (FloatingActionButton) findViewById(R.id.frontFlipButton);
        frontFlipBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bebop2Drone.makeAFlip(ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_ENUM.ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_FRONT);
            }
        });

        leftFlipBt = (FloatingActionButton) findViewById(R.id.leftFlipButton);
        leftFlipBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bebop2Drone.makeAFlip(ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_ENUM.ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_LEFT);
            }
        });

        rightFlipBt = (FloatingActionButton) findViewById(R.id.rightFlipButton);
        rightFlipBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bebop2Drone.makeAFlip(ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_ENUM.ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_RIGHT);
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

        findViewById(R.id.cameraButton).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                bebop2Drone.takePicture();
            }
        });

        videoBt = (FloatingActionButton) findViewById(R.id.videoButton);
        videoBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecording) {
                    bebop2Drone.stopVideo();
                    bebop2Drone.startVideo();
                } else
                    bebop2Drone.startVideo();
            }
        });

        findViewById(R.id.panoramaButton).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                count = 0;
                panoramaStarted = true;
                bebop2Drone.setRotationSpeed((float) 20);

                bebop2Drone.setFlag((byte) 0);
                if (panoramaVideo) {
                    bebop2Drone.stopVideo();
                    bebop2Drone.startVideo();
                }
                bebop2Drone.setYaw((byte) 100);
                chrono.stop();
                chrono.start();
            }
        });

        findViewById(R.id.paramsButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Builder dialogBuilder = new Builder(new ContextThemeWrapper(Bebop2Activity.this, R.style.AppTheme));
                LayoutInflater inflater = Bebop2Activity.this.getLayoutInflater();
                final View dialogView = inflater.inflate(R.layout.drone_options, null);
                dialogBuilder.setView(dialogView);

                final Switch autoRecordSwitch = (Switch) dialogView.findViewById(R.id.autoRecordSwitch);
                final Switch commandsInvertedSwitch = (Switch) dialogView.findViewById(R.id.commandsInverted);
                final Switch joysticksFixedSwitch = (Switch) dialogView.findViewById(R.id.joysticksFixed);
                final Switch joysticksInvertedSwitch = (Switch) dialogView.findViewById(R.id.joysticksInverted);
                final Switch panoramaPhotosSwitch = (Switch) dialogView.findViewById(R.id.panoramaPhotosSwitch);
                final Switch panoramaVideoSwitch = (Switch) dialogView.findViewById(R.id.panoramaVideoSwitch);

                autoRecordSwitch.setChecked(autoRecord);
                commandsInvertedSwitch.setChecked(commandsInverted);
                joysticksFixedSwitch.setChecked(joysticksFixed);
                joysticksInvertedSwitch.setChecked(joysticksInverted);
                panoramaPhotosSwitch.setChecked(panoramaPhotos);
                panoramaVideoSwitch.setChecked(panoramaVideo);

                dialogBuilder.setPositiveButton(R.string.validate, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        autoRecord = autoRecordSwitch.isChecked();
                        commandsInverted = commandsInvertedSwitch.isChecked();
                        joysticksFixed = joysticksFixedSwitch.isChecked();
                        joysticksInverted = joysticksInvertedSwitch.isChecked();
                        panoramaPhotos = panoramaPhotosSwitch.isChecked();
                        panoramaVideo = panoramaVideoSwitch.isChecked();

                        leftJoystick.setFixedCenter(joysticksFixed);
                        rightJoystick.setFixedCenter(joysticksFixed);
                        bebop2Drone.setLinearSpeed(linearSpeed);
                        bebop2Drone.setRotationSpeed(rotationSpeed);

                        if(joysticksInverted) {
                            leftJoystickUpView.setImageResource(commandsInverted ? R.drawable.ic_downward : R.drawable.ic_upward);
                            leftJoystickDownView.setImageResource(commandsInverted ? R.drawable.ic_upward : R.drawable.ic_downward);
                            leftJoystickLeftView.setImageResource(R.drawable.ic_left_rotation);
                            ((ViewGroup.MarginLayoutParams) leftJoystickLeftView.getLayoutParams()).setMargins(margins[2], margins[0], margins[0], margins[4]);
                            leftJoystickRightView.setImageResource(R.drawable.ic_right_rotation);
                            ((ViewGroup.MarginLayoutParams) leftJoystickRightView.getLayoutParams()).setMargins(margins[6], margins[0], margins[0], margins[4]);
                            rightJoystickUpView.setImageResource(R.drawable.ic_forward);
                            rightJoystickDownView.setImageResource(R.drawable.ic_backward);
                            rightJoystickLeftView.setImageResource(R.drawable.ic_left);
                            ((ViewGroup.MarginLayoutParams) rightJoystickLeftView.getLayoutParams()).setMargins(margins[0], margins[0], margins[5], margins[3]);
                            rightJoystickRightView.setImageResource(R.drawable.ic_right);
                            ((ViewGroup.MarginLayoutParams) rightJoystickRightView.getLayoutParams()).setMargins(margins[0], margins[0], margins[1], margins[3]);
                        } else {
                            leftJoystickUpView.setImageResource(R.drawable.ic_forward);
                            leftJoystickDownView.setImageResource(R.drawable.ic_backward);
                            leftJoystickLeftView.setImageResource(R.drawable.ic_left);
                            ((ViewGroup.MarginLayoutParams) leftJoystickLeftView.getLayoutParams()).setMargins(margins[1], margins[0], margins[0], margins[3]);
                            leftJoystickRightView.setImageResource(R.drawable.ic_right);
                            ((ViewGroup.MarginLayoutParams) leftJoystickRightView.getLayoutParams()).setMargins(margins[5], margins[0], margins[0], margins[3]);
                            rightJoystickUpView.setImageResource(commandsInverted ? R.drawable.ic_downward : R.drawable.ic_upward);
                            rightJoystickDownView.setImageResource(commandsInverted ? R.drawable.ic_upward : R.drawable.ic_downward);
                            rightJoystickLeftView.setImageResource(R.drawable.ic_left_rotation);
                            ((ViewGroup.MarginLayoutParams) rightJoystickLeftView.getLayoutParams()).setMargins(margins[0], margins[0], margins[6], margins[4]);
                            rightJoystickRightView.setImageResource(R.drawable.ic_right_rotation);
                            ((ViewGroup.MarginLayoutParams) rightJoystickRightView.getLayoutParams()).setMargins(margins[0], margins[0], margins[2], margins[4]);
                        }
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
                    bebop2Drone.setYaw((byte) 0);
                    if (panoramaVideo) {
                        bebop2Drone.stopVideo();
                        if (autoRecord)
                            bebop2Drone.startVideo();
                    }
                    bebop2Drone.setRotationSpeed(linearSpeed);
                }
                if (panoramaPhotos) {
                    if (count % 2 == 0 && count < timeRotate && panoramaStarted)
                        bebop2Drone.takePicture();
                }
                count++;
            }

        });
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
        public void onBatteryChargeChanged(int charge) {
            batteryTextView.setText(String.format(" %d%%", charge));
            switch (charge / 10) {
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
            switch (state) {
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
        public void onCodecConfigured(ARControllerCodec codec) {
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

            if (nbMedias > 0) {
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
        public void onDownloadCompleted(String mediaName) {
            currentDownloadIndex++;
            downloadProgressDialog.setSecondaryProgress(currentDownloadIndex * 100);

            if (currentDownloadIndex > nbMaxDownload) {
                downloadProgressDialog.dismiss();
                downloadProgressDialog = null;
            }
        }
    };

}
