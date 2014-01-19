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

package com.github.barcodeeye.scan;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import com.github.barcodeeye.BaseGlassActivity;
import com.github.barcodeeye.R;
import com.github.barcodeeye.image.ImageManager;
import com.github.barcodeeye.migrated.AmbientLightManager;
import com.github.barcodeeye.migrated.BeepManager;
import com.github.barcodeeye.migrated.DecodeFormatManager;
import com.github.barcodeeye.migrated.DecodeHintManager;
import com.github.barcodeeye.migrated.FinishListener;
import com.github.barcodeeye.migrated.InactivityTimer;
import com.github.barcodeeye.migrated.IntentSource;
import com.github.barcodeeye.migrated.Intents;
import com.github.barcodeeye.migrated.ScanFromWebPageManager;
import com.github.barcodeeye.scan.api.CardPresenter;
import com.github.barcodeeye.scan.result.ResultProcessor;
import com.github.barcodeeye.scan.result.ResultProcessorFactory;
import com.github.barcodeeye.scan.ui.ViewfinderView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.camera.CameraManager;
import com.google.zxing.client.android.clipboard.ClipboardInterface;
import com.google.zxing.client.result.ParsedResult;
import com.google.zxing.client.result.ResultParser;

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
    
    private static final String IMAGE_PREFIX = "BarcodeEye_";

    private static final long DEFAULT_INTENT_RESULT_DURATION_MS = 1500L;
    
    private static final String[] ZXING_URLS = { "http://zxing.appspot.com/scan", "zxing://scan/" };

    private static final Collection<ResultMetadataType> DISPLAYABLE_METADATA_TYPES = EnumSet
            .of(ResultMetadataType.ISSUE_NUMBER,
                    ResultMetadataType.SUGGESTED_PRICE,
                    ResultMetadataType.ERROR_CORRECTION_LEVEL,
                    ResultMetadataType.POSSIBLE_COUNTRY);

    private CameraManager mCameraManager;
    private CaptureActivityHandler mHandler;
    private Result mSavedResultToShow;
    private ViewfinderView mViewfinderView;
    private boolean mHasSurface;
    private IntentSource mSource;
    private String mSourceUrl;
    private ScanFromWebPageManager mScanFromWebPageManager;
    private Collection<BarcodeFormat> mDecodeFormats;
    private Map<DecodeHintType, ?> mDecodeHints;
    private String mCharacterSet;
    private InactivityTimer mInactivityTimer;
    private BeepManager mBeepManager;
    private AmbientLightManager mAmbientLightManager;
    private ImageManager mImageManager;

    public static Intent newIntent(Context context) {
        Intent intent = new Intent(context, CaptureActivity.class);
        return intent;
    }

    public ViewfinderView getViewfinderView() {
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

        mImageManager = new ImageManager(this);

        mHasSurface = false;
        mInactivityTimer = new InactivityTimer(this);
        mBeepManager = new BeepManager(this);
        mAmbientLightManager = new AmbientLightManager(this);

        mViewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);

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
        
        Intent intent = getIntent();

        mSource = IntentSource.NONE;
        mDecodeFormats = null;
        mCharacterSet = null;

        if (intent != null) {

          String action = intent.getAction();
          String dataString = intent.getDataString();

          if (Intents.Scan.ACTION.equals(action)) {

            // Scan the formats the intent requested, and return the result to the calling activity.
            mSource = IntentSource.NATIVE_APP_INTENT;
            mDecodeFormats = DecodeFormatManager.parseDecodeFormats(intent);
            mDecodeHints = DecodeHintManager.parseDecodeHints(intent);

            if (intent.hasExtra(Intents.Scan.WIDTH) && intent.hasExtra(Intents.Scan.HEIGHT)) {
              int width = intent.getIntExtra(Intents.Scan.WIDTH, 0);
              int height = intent.getIntExtra(Intents.Scan.HEIGHT, 0);
              if (width > 0 && height > 0) {
                mCameraManager.setManualFramingRect(width, height);
              }
            }
          } else if (dataString != null &&
                     dataString.contains("http://www.google") &&
                     dataString.contains("/m/products/scan")) {

            // Scan only products and send the result to mobile Product Search.
            mSource = IntentSource.PRODUCT_SEARCH_LINK;
            mSourceUrl = dataString;
            mDecodeFormats = DecodeFormatManager.PRODUCT_FORMATS;

          } else if (isZXingURL(dataString)) {

            // Scan formats requested in query string (all formats if none specified).
            // If a return URL is specified, send the results there. Otherwise, handle it ourselves.
            mSource = IntentSource.ZXING_LINK;
            mSourceUrl = dataString;
            Uri inputUri = Uri.parse(dataString);
            mScanFromWebPageManager = new ScanFromWebPageManager(inputUri);
            mDecodeFormats = DecodeFormatManager.parseDecodeFormats(inputUri);
            // Allow a sub-set of the hints to be specified by the caller.
            mDecodeHints = DecodeHintManager.parseDecodeHints(inputUri);

          }

          mCharacterSet = intent.getStringExtra(Intents.Scan.CHARACTER_SET);

        }
    }

    private static boolean isZXingURL(String dataString) {
      if (dataString == null) {
        return false;
      }
      for (String url : ZXING_URLS) {
        if (dataString.startsWith(url)) {
          return true;
        }
      }
      return false;
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
    protected boolean onTap() {
        openOptionsMenu();
        return super.onTap();
    }

    @Override
    protected void onDestroy() {
        mInactivityTimer.shutdown();
        super.onDestroy();
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
        ResultProcessor resultProcessor = ResultProcessorFactory.makeResultProcessor(this, rawResult);//, bitmap);//imageUri);
                
        boolean fromLiveScan = barcode != null;
        if (fromLiveScan) {
            // Then not from history, so beep/vibrate and we have an image to draw on
            mBeepManager.playBeepSoundAndVibrate();
            drawResultPoints(barcode, scaleFactor, rawResult, getResources()
                    .getColor(R.color.result_points));
        }

        switch (mSource) {
          case NATIVE_APP_INTENT:
          case PRODUCT_SEARCH_LINK:
            handleDecodeExternally(rawResult, resultProcessor, barcode);
            break;
          case ZXING_LINK:
            if (mScanFromWebPageManager == null || !mScanFromWebPageManager.isScanFromWebPage()) {
              handleDecodeInternally(rawResult, resultProcessor, barcode);
            } else {
              handleDecodeExternally(rawResult, resultProcessor, barcode);
            }
            break;
          case NONE:
            handleDecodeInternally(rawResult, resultProcessor, barcode);
            break;
        }
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
    private void handleDecodeInternally(Result rawResult, ResultProcessor resultProcessor, Bitmap barcode) {
      startActivity(ResultsActivity.newIntent(this,
              resultProcessor.getCardResults(), rawResult, barcode));
    }
    
    // Briefly show the contents of the barcode, then handle the result outside Barcode Scanner.
    private void handleDecodeExternally(Result rawResult, ResultProcessor resultProcessor, Bitmap barcode) {

      if (barcode != null) {
        mViewfinderView.drawResultBitmap(barcode);
      }
      
      long resultDurationMS;
      if (getIntent() == null) {
        resultDurationMS = DEFAULT_INTENT_RESULT_DURATION_MS;
      } else {
        resultDurationMS = getIntent().getLongExtra(Intents.Scan.RESULT_DISPLAY_DURATION_MS,
                                                    DEFAULT_INTENT_RESULT_DURATION_MS);
      }
      
      if (resultDurationMS > 0) {
        String rawResultString = String.valueOf(rawResult);
        if (rawResultString.length() > 32) {
          rawResultString = rawResultString.substring(0, 32) + " ...";
        }
        //statusView.setText(getString(resultHandler.getDisplayTitle()) + " : " + rawResultString);
      }

      /*
      if (copyToClipboard && !resultHandler.areContentsSecure()) {
        CharSequence text = resultHandler.getDisplayContents();
        ClipboardInterface.setText(text, this);
      }
      */
      
      if (mSource == IntentSource.NATIVE_APP_INTENT) {
        // Hand back whatever action they requested - this can be changed to Intents.Scan.ACTION when
        // the deprecated intent is retired.
        Intent intent = new Intent(getIntent().getAction());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        intent.putExtra(Intents.Scan.RESULT, rawResult.toString());
        intent.putExtra(Intents.Scan.RESULT_FORMAT, rawResult.getBarcodeFormat().toString());
        byte[] rawBytes = rawResult.getRawBytes();
        if (rawBytes != null && rawBytes.length > 0) {
          intent.putExtra(Intents.Scan.RESULT_BYTES, rawBytes);
        }
        Map<ResultMetadataType,?> metadata = rawResult.getResultMetadata();
        if (metadata != null) {
          if (metadata.containsKey(ResultMetadataType.UPC_EAN_EXTENSION)) {
            intent.putExtra(Intents.Scan.RESULT_UPC_EAN_EXTENSION,
                            metadata.get(ResultMetadataType.UPC_EAN_EXTENSION).toString());
          }
          Number orientation = (Number) metadata.get(ResultMetadataType.ORIENTATION);
          if (orientation != null) {
            intent.putExtra(Intents.Scan.RESULT_ORIENTATION, orientation.intValue());
          }
          String ecLevel = (String) metadata.get(ResultMetadataType.ERROR_CORRECTION_LEVEL);
          if (ecLevel != null) {
            intent.putExtra(Intents.Scan.RESULT_ERROR_CORRECTION_LEVEL, ecLevel);
          }
          @SuppressWarnings("unchecked")
          Iterable<byte[]> byteSegments = (Iterable<byte[]>) metadata.get(ResultMetadataType.BYTE_SEGMENTS);
          if (byteSegments != null) {
            int i = 0;
            for (byte[] byteSegment : byteSegments) {
              intent.putExtra(Intents.Scan.RESULT_BYTE_SEGMENTS_PREFIX + i, byteSegment);
              i++;
            }
          }
        }
        sendReplyMessage(R.id.return_scan_result, intent, resultDurationMS);
      } else if (mSource == IntentSource.PRODUCT_SEARCH_LINK) {
        
        // Reformulate the URL which triggered us into a query, so that the request goes to the same
        // TLD as the scan URL.
        int end = mSourceUrl.lastIndexOf("/scan");
        String replyURL = mSourceUrl.substring(0, end) + "?q=" + resultProcessor.getDisplayContents() + "&source=zxing";      
        sendReplyMessage(R.id.launch_product_query, replyURL, resultDurationMS);
        
      } else if (mSource == IntentSource.ZXING_LINK) {

        if (mScanFromWebPageManager != null && mScanFromWebPageManager.isScanFromWebPage()) {
          String replyURL = mScanFromWebPageManager.buildReplyURL(rawResult, resultProcessor);
          sendReplyMessage(R.id.launch_product_query, replyURL, resultDurationMS);
        }
        
      }
    }

    private void sendReplyMessage(int id, Object arg, long delayMS) {
      if (mHandler != null) {
        Message message = Message.obtain(mHandler, id, arg);
        if (delayMS > 0L) {
          mHandler.sendMessageDelayed(message, delayMS);
        } else {
          mHandler.sendMessage(message);
        }
      }
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
                mHandler = new CaptureActivityHandler(this, null, mDecodeHints,
                        null, mCameraManager);
            }

            decodeOrStoreSavedBitmap(null, null);
        } catch (IOException e) {
            Log.w(TAG, e);
            displayFrameworkBugMessageAndExit();
        } catch (InterruptedException e) {
            Log.w(TAG, e);
            displayFrameworkBugMessageAndExit();
        }
    }

    /**
     * FIXME: This should be a glass compatible view (Card)
     */
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
    }

    public void drawViewfinder() {
        mViewfinderView.drawViewfinder();
    }
}
