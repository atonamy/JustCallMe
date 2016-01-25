package com.atonamy.justcallme;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import java.util.Random;

public class StartActivity extends AppCompatActivity {

    private ImageButton startCallButton;
    private ZoomViewAnimation buttonAnimation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        setupActionBar();
        setupUI();
    }

    protected void startCall(int delay, final boolean unock) {

        (new Handler()).postDelayed(new Runnable(){
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(isOnline()) {
                            Intent video_call = new Intent(StartActivity.this, VideoCallActivity.class);
                            startActivityForResult(video_call, 1);
                            if(unock)
                                startCallButton.setEnabled(true);
                        } else
                            Toast.makeText(getApplicationContext(), "No networking connection. Please check your settings.",
                                    Toast.LENGTH_LONG).show();
                    }
                });

            }}, delay);
    }

    protected void setupActionBar() {
        ActionBar action_bar = getSupportActionBar();
        action_bar.setDisplayShowHomeEnabled(true);
        action_bar.setIcon(R.mipmap.ic_launcher);
        action_bar.setTitle("  " + action_bar.getTitle());
    }

    protected void setupUI() {
        startCallButton = (ImageButton)findViewById(R.id.imageButtonCall);
        buttonAnimation = new ZoomViewAnimation(startCallButton);

        startCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                buttonAnimation.performAnimation();
                startCall(200, false);
            }
        });
    }

    protected boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    protected void startWithRandomDelay() {
        startCallButton.setEnabled(false);
        int randomDelay = new Random().nextInt(1000) + 2000;
        startCall(randomDelay, true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        /*Toast.makeText(getApplicationContext(), "code:" + requestCode + " result:" + resultCode + " restartCall:" +  VideoCallActivity.restartCall,
                Toast.LENGTH_LONG).show();*/
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            int result = data.getIntExtra("result", 0);
            if(result == -1) {
                startCallButton.setEnabled(false);
                int randomDelay = new Random().nextInt(1000) + 2000;
                startCall(randomDelay, true);
            }
        }
    }
}


