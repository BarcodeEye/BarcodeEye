/*
 * Copyright (C) 2008 ZXing authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.barcodeeye.migrated;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.barcodeeye.BaseGlassActivity;
import com.github.barcodeeye.R;
import com.github.barcodeeye.migrated.result.ResultHandler;
import com.github.barcodeeye.migrated.result.ResultHandlerFactory;
import com.github.barcodeeye.migrated.result.supplement.SupplementalInfoRetriever;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.camera.CameraManager;

/**
 * This activity opens the camera and does the actual scanning on a background
 * thread. It draws a
 * viewfinder to help the user place the barcode correctly, shows feedback as
 * the image processing
 * is happening, and then overlays the results when a scan is successful.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
public final class CaptureActivity extends BaseGlassActivity implements
        SurfaceHolder.Callback {

    private static final String TAG = CaptureActivity.class.getSimpleName();

    private static final Collection<ResultMetadataType> DISPLAYABLE_METADATA_TYPES = EnumSet
            .of(ResultMetadataType.ISSUE_NUMBER,
                    ResultMetadataType.SUGGESTED_PRICE,
                    ResultMetadataType.ERROR_CORRECTION_LEVEL,
                    ResultMetadataType.POSSIBLE_COUNTRY);

    private CameraManager mCameraManager;
    private CaptureActivityHandler mHandler;
    private Result mSavedResultToShow;
    private ViewfinderView mViewfinderView;
    private TextView mStatusView;
    private View mResultView;
    private boolean mHasSurface;
    private Map<DecodeHintType, ?> mDecodeHints;
    private InactivityTimer mInactivityTimer;
    private BeepManager mBeepManager;
    private AmbientLightManager mAmbientLightManager;

    private ImageView mBarcodeImageView;

    private ResultHandler mCurrentResultsHandler;

    public static Intent newIntent(Context context) {
        Intent intent = new Intent(context, CaptureActivity.class);
        return intent;
    }

    ViewfinderView getViewfinderView() {
        return mViewfinderView;
    }

    public Handler getHandler() {
        return mHandler;
    }

    CameraManager getCameraManager() {
        return mCameraManager;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_capture);

        mHasSurface = false;
        mInactivityTimer = new InactivityTimer(this);
        mBeepManager = new BeepManager(this);
        mAmbientLightManager = new AmbientLightManager(this);

        mBarcodeImageView = (ImageView) findViewById(R.id.barcode_image_view);
        mViewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);

        mResultView = findViewById(R.id.result_view);
        mStatusView = (TextView) findViewById(R.id.status_view);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // CameraManager must be initialized here, not in onCreate(). This is necessary because we don't
        // want to open the camera driver and measure the screen size if we're going to show the help on
        // first launch. That led to bugs where the scanning rectangle was the wrong size and partially
        // off screen.
        mCameraManager = new CameraManager(getApplication());
        mViewfinderView.setCameraManager(mCameraManager);

        mHandler = null;

        resetStatusView();

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (mHasSurface) {
            // The activity was paused but not stopped, so the surface still exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.
            initCamera(surfaceHolder);
        } else {
            // Install the callback and wait for surfaceCreated() to init the camera.
            surfaceHolder.addCallback(this);
        }

        mBeepManager.updatePrefs();
        mAmbientLightManager.start(mCameraManager);

        mInactivityTimer.onResume();
    }

    @Override
    protected void onPause() {
        if (mHandler != null) {
            mHandler.quitSynchronously();
            mHandler = null;
        }
        mInactivityTimer.onPause();
        mAmbientLightManager.stop();
        mCameraManager.closeDriver();
        if (!mHasSurface) {
            SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback(this);
        }
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (mCurrentResultsHandler != null) {
            restartPreviewAfterDelay(0L);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected boolean onTap() {
        openOptionsMenu();
        return super.onTap();
    }

    @Override
    protected void onDestroy() {
        mInactivityTimer.shutdown();
        super.onDestroy();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        menu.clear();

        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.capture, menu);
        if (mCurrentResultsHandler != null) {
            int optionCount = mCurrentResultsHandler.getOptionCount();
            for (int i = 0; i < optionCount; i++) {
                menu.add(0, i, i, mCurrentResultsHandler.getOptionText(i));
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.v(TAG, "Option selected: " + item.getTitle());
        switch (item.getItemId()) {
            case R.id.menu_settings:
                return true;
            case R.id.menu_help:
                return true;
            default:
                try {
                    mCurrentResultsHandler.handleOptionClicked(item.getItemId());
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, e.getMessage(), e);
                    Toast.makeText(this, "Not supported!", Toast.LENGTH_SHORT).show();
                }
                return super.onOptionsItemSelected(item);
        }
    }

    private void decodeOrStoreSavedBitmap(Bitmap bitmap, Result result) {
        // Bitmap isn't used yet -- will be used soon
        if (mHandler == null) {
            mSavedResultToShow = result;
        } else {
            if (result != null) {
                mSavedResultToShow = result;
            }
            if (mSavedResultToShow != null) {
                Message message = Message.obtain(mHandler,
                        R.id.decode_succeeded, mSavedResultToShow);
                mHandler.sendMessage(message);
            }
            mSavedResultToShow = null;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (holder == null) {
            Log.e(TAG,
                    "*** WARNING *** surfaceCreated() gave us a null surface!");
        }
        if (!mHasSurface) {
            mHasSurface = true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mHasSurface = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {

    }

    /**
     * A valid barcode has been found, so give an indication of success and show
     * the results.
     *
     * @param rawResult
     *            The contents of the barcode.
     * @param scaleFactor
     *            amount by which thumbnail was scaled
     * @param barcode
     *            A greyscale bitmap of the camera data which was decoded.
     */
    public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {
        mInactivityTimer.onActivity();
        ResultHandler resultHandler = ResultHandlerFactory.makeResultHandler(
                this, rawResult);

        boolean fromLiveScan = barcode != null;
        if (fromLiveScan) {
            mBeepManager.playBeepSoundAndVibrate();
            drawResultPoints(barcode, scaleFactor, rawResult, getResources().getColor(R.color.result_points));
        }

        handleDecodeInternally(rawResult, resultHandler, barcode);
    }

    /**
     * Superimpose a line for 1D or dots for 2D to highlight the key features of
     * the barcode.
     *
     * @param barcode
     *            A bitmap of the captured image.
     * @param scaleFactor
     *            amount by which thumbnail was scaled
     * @param rawResult
     *            The decoded results which contains the points to draw.
     */
    private static void drawResultPoints(Bitmap barcode, float scaleFactor,
            Result rawResult, int color) {
        ResultPoint[] points = rawResult.getResultPoints();
        if (points != null && points.length > 0) {
            Canvas canvas = new Canvas(barcode);
            Paint paint = new Paint();
            paint.setColor(color);
            if (points.length == 2) {
                paint.setStrokeWidth(4.0f);
                drawLine(canvas, paint, points[0], points[1], scaleFactor);
            } else if (points.length == 4
                    && (rawResult.getBarcodeFormat() == BarcodeFormat.UPC_A || rawResult
                            .getBarcodeFormat() == BarcodeFormat.EAN_13)) {
                // Hacky special case -- draw two lines, for the barcode and metadata
                drawLine(canvas, paint, points[0], points[1], scaleFactor);
                drawLine(canvas, paint, points[2], points[3], scaleFactor);
            } else {
                paint.setStrokeWidth(10.0f);
                for (ResultPoint point : points) {
                    if (point != null) {
                        canvas.drawPoint(scaleFactor * point.getX(),
                                scaleFactor * point.getY(), paint);
                    }
                }
            }
        }
    }

    private static void drawLine(Canvas canvas, Paint paint, ResultPoint a,
            ResultPoint b, float scaleFactor) {
        if (a != null && b != null) {
            canvas.drawLine(scaleFactor * a.getX(), scaleFactor * a.getY(),
                    scaleFactor * b.getX(), scaleFactor * b.getY(), paint);
        }
    }

    // Put up our own UI for how to handle the decoded contents.
    private void handleDecodeInternally(Result rawResult,
            ResultHandler resultHandler, Bitmap barcode) {
        mStatusView.setVisibility(View.GONE);
        mViewfinderView.setVisibility(View.GONE);
        mResultView.setVisibility(View.VISIBLE);
        mBarcodeImageView.setVisibility(View.VISIBLE);

        if (barcode == null) {
            mBarcodeImageView.setImageBitmap(BitmapFactory.decodeResource(
                    getResources(), R.drawable.ic_launcher));
        } else {
            mBarcodeImageView.setImageBitmap(barcode);
        }

        TextView formatTextView = (TextView) findViewById(R.id.format_text_view);
        formatTextView.setText(rawResult.getBarcodeFormat().toString());

        TextView typeTextView = (TextView) findViewById(R.id.type_text_view);
        typeTextView.setText(resultHandler.getType().toString());

        TextView metaTextView = (TextView) findViewById(R.id.meta_text_view);
        View metaTextViewLabel = findViewById(R.id.meta_text_view_label);
        metaTextView.setVisibility(View.GONE);
        metaTextViewLabel.setVisibility(View.GONE);
        Map<ResultMetadataType, Object> metadata = rawResult
                .getResultMetadata();
        if (metadata != null) {
            StringBuilder metadataText = new StringBuilder(20);
            for (Map.Entry<ResultMetadataType, Object> entry : metadata
                    .entrySet()) {
                if (DISPLAYABLE_METADATA_TYPES.contains(entry.getKey())) {
                    metadataText.append(entry.getValue()).append('\n');
                }
            }
            if (metadataText.length() > 0) {
                metadataText.setLength(metadataText.length() - 1);
                metaTextView.setText(metadataText);
                metaTextView.setVisibility(View.VISIBLE);
                metaTextViewLabel.setVisibility(View.VISIBLE);
            }
        }

        TextView contentsTextView = (TextView) findViewById(R.id.contents_text_view);
        CharSequence displayContents = resultHandler.getDisplayContents();
        contentsTextView.setText(displayContents);

        TextView supplementTextView = (TextView) findViewById(R.id.contents_supplement_text_view);
        supplementTextView.setText("");
        supplementTextView.setOnClickListener(null);
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                PreferencesActivity.KEY_SUPPLEMENTAL, true)) {
            SupplementalInfoRetriever.maybeInvokeRetrieval(supplementTextView,
                    resultHandler.getResult(), this);
        }

        mCurrentResultsHandler = resultHandler;
        invalidateOptionsMenu();
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        if (mCameraManager.isOpen()) {
            Log.w(TAG,
                    "initCamera() while already open -- late SurfaceView callback?");
            return;
        }
        try {
            mCameraManager.openDriver(surfaceHolder);
            // Creating the handler starts the preview, which can also throw a RuntimeException.
            if (mHandler == null) {
                mHandler = new CaptureActivityHandler(this, null,
                        mDecodeHints, null, mCameraManager);
            }

            decodeOrStoreSavedBitmap(null, null);
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
            displayFrameworkBugMessageAndExit();
        } catch (RuntimeException e) {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
            Log.w(TAG, "Unexpected error initializing camera", e);
            displayFrameworkBugMessageAndExit();
        }
    }

    private void displayFrameworkBugMessageAndExit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.app_name));
        builder.setMessage(getString(R.string.msg_camera_framework_bug));
        builder.setPositiveButton(R.string.button_ok, new FinishListener(this));
        builder.setOnCancelListener(new FinishListener(this));
        builder.show();
    }

    public void restartPreviewAfterDelay(long delayMS) {
        if (mHandler != null) {
            mHandler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
        }
        resetStatusView();
    }

    private void resetStatusView() {
        mResultView.setVisibility(View.GONE);
        mBarcodeImageView.setVisibility(View.GONE);
        mStatusView.setText(R.string.msg_default_status);
        mStatusView.setVisibility(View.VISIBLE);
        mViewfinderView.setVisibility(View.VISIBLE);
        mCurrentResultsHandler = null;
        invalidateOptionsMenu();
    }

    public void drawViewfinder() {
        mViewfinderView.drawViewfinder();
    }
}
