package com.github.barcodeeye;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;

import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;
import com.google.android.glass.touchpad.GestureDetector.FingerListener;
import com.google.android.glass.touchpad.GestureDetector.ScrollListener;

public class BaseGlassActivity extends Activity implements FingerListener, ScrollListener {

    private static final String TAG = BaseGlassActivity.class.getSimpleName();
    private GestureDetector mGestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mGestureDetector = createGestureDetector(this);
    }

    protected boolean onTap() {
        return false;
    }

    protected boolean onTwoTap() {
        return false;
    }

    protected boolean onSwipeRight() {
        return false;
    }

    protected boolean onSwipeLeft() {
        return false;
    }

    @Override
    public void onFingerCountChanged(int arg0, int arg1) {

    }

    @Override
    public boolean onScroll(float arg0, float arg1, float arg2) {
        return false;
    }

    /*
     * Send generic motion events to the gesture detector
     */
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mGestureDetector != null) {
            return mGestureDetector.onMotionEvent(event);
        }
        return false;
    }

    private GestureDetector createGestureDetector(Context context) {
        GestureDetector gestureDetector = new GestureDetector(context);
        //Create a base listener for generic gestures
        gestureDetector.setBaseListener(new GestureDetector.BaseListener() {
            @Override
            public boolean onGesture(Gesture gesture) {
                if (gesture == Gesture.TAP) {
                    Log.v(TAG, "onSwipeTap");
                    return onTap();
                } else if (gesture == Gesture.TWO_TAP) {
                    Log.v(TAG, "onSwipeTwoTap");
                    return onTwoTap();
                } else if (gesture == Gesture.SWIPE_RIGHT) {
                    Log.v(TAG, "onSwipeRight");
                    return onSwipeRight();
                } else if (gesture == Gesture.SWIPE_LEFT) {
                    Log.v(TAG, "onSwipeLeft");
                    return onSwipeLeft();
                }
                return false;
            }
        });

        gestureDetector.setFingerListener(this);
        gestureDetector.setScrollListener(this);
        return gestureDetector;
    }
}
