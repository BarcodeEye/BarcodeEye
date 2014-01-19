package com.github.barcodeeye.scan.result;

import java.util.List;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.github.barcodeeye.scan.api.CardPresenter;
import com.google.zxing.Result;
import com.google.zxing.client.result.ParsedResult;
import com.google.zxing.client.result.ParsedResultType;

/**
 * @author javier.romero
 */
public abstract class ResultProcessor {
    private final ParsedResult mResult;
    private final Context mContext;
    private final Result mRawResult;
    //private final Uri mPhotoUri;

    public ResultProcessor(Context context, ParsedResult result,
            Result rawResult/*, Uri photoUri*/) {
        mResult = result;
        mContext = context;
        mRawResult = rawResult;
        //mPhotoUri = photoUri;
    }

    public Context getContext() {
        return mContext;
    }

    public ParsedResult getResult() {
        return mResult;
    }

    /*
    public Result getRawResult() {
        return mRawResult;
    }
    */
    
    /**
     * Create a possibly styled string for the contents of the current barcode.
     *
     * @return The text to be displayed.
     */
    public CharSequence getDisplayContents() {
      String contents = mResult.getDisplayResult();
      return contents.replace("\r", "");
    }

    /**
     * A convenience method to get the parsed type. Should not be overridden.
     *
     * @return The parsed type, e.g. URI or ISBN
     */
    public final ParsedResultType getType() {
      return mResult.getType();
    }

    /*
    public Uri getPhotoUri() {
        return mPhotoUri;
    }
    */

    public abstract List<CardPresenter> getCardResults();

    public static PendingIntent createPendingIntent(Context context,
            Intent intent) {
        return PendingIntent.getActivity(context, 0, intent, 0);
    }
}
