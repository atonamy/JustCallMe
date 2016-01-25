package com.atonamy.justcallme;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.RendererCommon;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class VideoCallActivity extends AppCompatActivity implements PeerConnectionClient.PeerConnectionEvents, SignalingHandler.Events {

    private static String WS_SERVER = "ws://104.199.139.87:8005";

    private static final int REMOTE_X = 0;
    private static final int REMOTE_Y = 0;
    private static final int REMOTE_WIDTH = 100;
    private static final int REMOTE_HEIGHT = 100;

    private static final int LOCAL_X_CONNECTED = 72;
    private static final int LOCAL_Y_CONNECTED = 0;
    private static final int LOCAL_WIDTH_CONNECTED = 25;
    private static final int LOCAL_HEIGHT_CONNECTED = 25;

    private static final int LOCAL_X_CONNECTING = 0;
    private static final int LOCAL_Y_CONNECTING = 0;
    private static final int LOCAL_WIDTH_CONNECTING = 100;
    private static final int LOCAL_HEIGHT_CONNECTING = 100;

    private RendererCommon.ScalingType scalingType;
    private SurfaceViewRenderer localRender;
    private SurfaceViewRenderer remoteRender;
    private PercentFrameLayout localRenderLayout;
    private PercentFrameLayout remoteRenderLayout;

    private boolean iceConnected;
    private boolean isInitiator;
    private boolean peerFound;
    private PeerConnectionClient peerConnectionClient;
    private SignalingHandler signalingHandler;
    SessionDescription sessionDescription;
    List<IceCandidate> iceCandidates;
    private EglBase rootEglBase;
    private WebRTCAudioManager audioManager;
    private boolean isAnswered = false;

    private View mBackgroundView;
    private ProgressBar mProgressBar;
    private TextView mTextWait;
    private int remoteScreenOrientation;


    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements

            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mEndCallListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            finish();
            return false;
        }
    };

    private final View.OnTouchListener mSwitchCameraListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            peerConnectionClient.switchCamera();
            return false;
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_call);

        initDefaults();
        initUI();
        initListeners();
        initVideoRenders();

        hideActionBar();
        keepScreenOn();

    }

    protected void initUI() {
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.fullscreen_content);
        mBackgroundView = findViewById(R.id.backgroundShading);
        mProgressBar = (ProgressBar)findViewById(R.id.progressBarWait);
        mTextWait = (TextView) findViewById(R.id.textViewWait);
        localRender = (SurfaceViewRenderer) findViewById(R.id.local_video_view);
        remoteRender = (SurfaceViewRenderer) findViewById(R.id.remote_video_view);
        localRenderLayout = (PercentFrameLayout) findViewById(R.id.local_video_layout);
        remoteRenderLayout = (PercentFrameLayout) findViewById(R.id.remote_video_layout);
        mProgressBar.getIndeterminateDrawable().setColorFilter(Color.LTGRAY,
                android.graphics.PorterDuff.Mode.MULTIPLY);
    }

    protected void initDefaults() {
        mVisible = true;
        iceConnected = false;
        isInitiator = false;

        peerConnectionClient = PeerConnectionClient.getInstance();
        peerConnectionClient.createPeerConnectionFactory(this, getPeerConnectionParameters(), this);
        signalingHandler = new SignalingHandler(this, WS_SERVER);
        peerFound = false;
        sessionDescription = null;
        iceCandidates = new LinkedList<IceCandidate>();
        remoteScreenOrientation = Configuration.ORIENTATION_UNDEFINED;
    }

    protected void initListeners() {
        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.end_call_button).setOnTouchListener(mEndCallListener);
        findViewById(R.id.switch_camera_button).setOnTouchListener(mSwitchCameraListener);
    }

    protected void hideActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();
    }

    protected void keepScreenOn() {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        getWindow().setAttributes(params);
    }

    protected void initVideoRenders() {
        scalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL;
        rootEglBase = EglBase.create();
        localRender.init(rootEglBase.getEglBaseContext(), null);
        remoteRender.init(rootEglBase.getEglBaseContext(), null);
        localRender.setZOrderMediaOverlay(true);
        updateVideoView();

    }

    protected PeerConnectionClient.PeerConnectionParameters getPeerConnectionParameters() {
        return new PeerConnectionClient.PeerConnectionParameters(
                true,
                false,
                0,
                0,
                0,
                0,
                "VP8",
                true,
                0,
                "OPUS",
                false,
                false);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.

        startCall();
        delayedHide(100);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (peerConnectionClient != null) {
            peerConnectionClient.stopVideoSource();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (peerConnectionClient != null) {
            peerConnectionClient.startVideoSource();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int orientation = getScreenOrientation();
        if(iceConnected) {
            signalingHandler.sendScreenOrientation(orientation);
            adjustScreenOrientation();
        }
    }

    @Override
    protected void onDestroy() {

        releaseAll();
        super.onDestroy();
    }

    public int getScreenOrientation() {
        Display getOrient = getWindowManager().getDefaultDisplay();
        int rotation = getOrient.getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                return Configuration.ORIENTATION_PORTRAIT;
            case Surface.ROTATION_90:
                return Configuration.ORIENTATION_LANDSCAPE;
            case Surface.ROTATION_180:
                return Configuration.ORIENTATION_PORTRAIT;
            case Surface.ROTATION_270:
                return Configuration.ORIENTATION_LANDSCAPE;
            default:
                return Configuration.ORIENTATION_UNDEFINED;
        }
    }


    private void releaseAll() {

        if (peerConnectionClient != null) {
            peerConnectionClient.stopVideoSource();
        }
        if (signalingHandler.getConnectionStatus())
            signalingHandler.disconnect();

        rootEglBase.release();

        if (peerConnectionClient != null) {
            peerConnectionClient.close();
        }
        if (localRender != null) {
            localRender.release();
        }
        if (remoteRender != null) {
            remoteRender.release();
        }
        if (audioManager != null) {
            audioManager.close();
        }
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first

        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    private void startCall() {

        signalingHandler.connect();
        // Create and audio manager that will take care of audio routing,
        // audio modes, audio device enumeration etc.
        audioManager = WebRTCAudioManager.create(this, new Runnable() {
                    // This method will be called each time the audio state (number and
                    // type of devices) has been changed.
                    @Override
                    public void run() {
                        onAudioManagerChangedState();
                    }

                    private void onAudioManagerChangedState() {
                    }
                }
        );
        // Store existing audio settings and change audio mode to
        // MODE_IN_COMMUNICATION for best possible VoIP performance.
        audioManager.init();
    }

    private void updateVideoView() {
        remoteRenderLayout.setPosition(REMOTE_X, REMOTE_Y, REMOTE_WIDTH, REMOTE_HEIGHT);
        remoteRender.setScalingType(scalingType);
        remoteRender.setMirror(false);

        if (iceConnected) {
            localRenderLayout.setPosition(
                    LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED, LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED);
            localRender.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        } else {
            localRenderLayout.setPosition(
                    LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING, LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING);
            localRender.setScalingType(scalingType);
        }
        localRender.setMirror(false);

        localRender.requestLayout();
        remoteRender.requestLayout();
    }

    private void adjustScreenOrientation() {
        if(remoteScreenOrientation == getScreenOrientation())
            remoteRender.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        else
            remoteRender.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        remoteRender.requestLayout();
    }

    @Override
    public void onInitiator(final List<PeerConnection.IceServer> iceServers) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                isInitiator = true;
                peerConnectionClient.createPeerConnection(localRender, remoteRender, iceServers);
                peerConnectionClient.createOffer();
                Toast.makeText(getApplicationContext(), "Waiting for call...",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onServerConnected(final String connectionId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "Connected",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onInterlocutorDisconnected() {
        (new Handler()).postDelayed(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                                  @Override
                                  public void run () {
                                      Toast.makeText(getApplicationContext(), "Disconnected",
                                              Toast.LENGTH_LONG).show();
                                      finish();
                                  }
                              }
                );
            }

        }, 2000);
    }

    @Override
    public void onInterlocutorFound(final String peerId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                peerFound = true;
                if(sessionDescription != null)
                    signalingHandler.sendLocalDescription(isInitiator, sessionDescription);
                if(iceCandidates.size() > 0)
                {
                    Iterator<IceCandidate> i_candidate = iceCandidates.iterator();
                    while(i_candidate.hasNext())
                        signalingHandler.sendLocalCandidate(i_candidate.next());
                    iceCandidates.clear();

                }
                mTextWait.setText(getResources().getString(R.string.call));
                Toast.makeText(getApplicationContext(), "Someone just joined",
                        Toast.LENGTH_SHORT).show();

                (new Handler()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                                          @Override
                                          public void run () {
                                              mTextWait.setText(getResources().getString(R.string.wait));
                                          }
                                      }
                        );
                    }

                }, 3000);
            }
        });
    }

    @Override
    public void onRemoteDescription(final SessionDescription description, final List<PeerConnection.IceServer> iceServers) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(!isInitiator)
                    peerConnectionClient.createPeerConnection(localRender, remoteRender, iceServers);


                peerConnectionClient.setRemoteDescription(description);

                if(!isInitiator) {
                    Toast.makeText(getApplicationContext(), "Replying on call",
                            Toast.LENGTH_SHORT).show();
                    peerConnectionClient.createAnswer();
                    isAnswered = true;
                }

                mProgressBar.getIndeterminateDrawable().setColorFilter(Color.RED,
                        android.graphics.PorterDuff.Mode.MULTIPLY);

                signalingHandler.descriptionSent();
            }
        });
    }

    @Override
    public void onRemoteCandidate(final IceCandidate candidate) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(!isAnswered && !isInitiator) {
                    Toast.makeText(getApplicationContext(), "No answer created :(",
                            Toast.LENGTH_LONG).show();
                    finish();
                }
                else {
                    peerConnectionClient.addRemoteIceCandidate(candidate);

                    mProgressBar.getIndeterminateDrawable().setColorFilter(Color.GREEN,
                            android.graphics.PorterDuff.Mode.MULTIPLY);
                }

            }
        });
    }

    @Override
    public void onError(final Integer code, final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), ((message == null) ? "Something went wrong :(":message),
                        Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    @Override
    public void onRemoteScreenOrientation(final int orientation) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                remoteScreenOrientation = orientation;
                if(iceConnected)
                    adjustScreenOrientation();
            }
        });

    }

    @Override
    public void onLocalDescription(final SessionDescription description) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(peerFound && signalingHandler.getConnectionStatus())
                    signalingHandler.sendLocalDescription(isInitiator, description);
                else
                    sessionDescription = description;
            }
        });
    }

    @Override
    public void onIceCandidate(final IceCandidate candidate) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(peerFound && signalingHandler.getConnectionStatus())
                    signalingHandler.sendLocalCandidate(candidate);
                else
                    iceCandidates.add(candidate);
            }
        });
    }

    @Override
    public void onIceConnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mBackgroundView.setVisibility(View.GONE);
                mTextWait.setVisibility(View.GONE);
                mProgressBar.setVisibility(View.GONE);
                iceConnected = true;
                Toast.makeText(getApplicationContext(), "Ready to talk!",
                        Toast.LENGTH_SHORT).show();
                updateVideoView();
                signalingHandler.sendScreenOrientation(getScreenOrientation());
            }
        });
    }

    @Override
    public void onIceDisconnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "Connection is lost",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    @Override
    public void onPeerConnectionClosed() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        });
    }

    @Override
    public void onPeerConnectionStatsReady(StatsReport[] reports) {

    }

    @Override
    public void onPeerConnectionError(String description) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "Poor connection, trying to reconnect...",
                        Toast.LENGTH_LONG).show();
                Intent returnIntent = new Intent();
                returnIntent.putExtra("result", -1);
                setResult(Activity.RESULT_OK, returnIntent);
                finish();
            }
        });
    }

}
