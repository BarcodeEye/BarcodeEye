package com.github.barcodeeye;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.github.barcodeeye.R;
import com.github.barcodeeye.scan.CaptureActivity;

public class LaunchActivity extends Activity {

    private static final String TAG = LaunchActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);

        // delayed camera activity
        // see: https://code.google.com/p/google-glass-api/issues/detail?id=259
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
//                try {
//                    ActivityInfo activityInfo = getPackageManager().getActivityInfo(getComponentName(), 0);
//                    processVoiceAction(activityInfo.loadLabel(getPackageManager()).toString());
//                } catch (NameNotFoundException e) {
//                    e.printStackTrace();
                    processVoiceAction(null);
//                }
            }
        }, 100);

    }

    private void processVoiceAction(String command) {
        Log.v(TAG, "Voice command: " + command);
//        if (command == null) {
//            startActivity(CaptureActivity.newIntent(this, ScanAction.UNDEFINED));
//        } else if (command.toLowerCase().contains("product")) {
            startActivity(CaptureActivity.newIntent(this));
//        } else {
//            startActivity(CaptureActivity.newIntent(this, ScanAction.UNDEFINED));
//        }

        finish();
    }
}
