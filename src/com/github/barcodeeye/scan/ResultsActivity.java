package com.github.barcodeeye.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

import com.github.barcodeeye.migrated.Intents;
import com.github.barcodeeye.scan.api.CardPresenter;
import com.google.android.glass.widget.CardScrollAdapter;
import com.google.android.glass.widget.CardScrollView;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;

public class ResultsActivity extends Activity {

    private static final String TAG = ResultsActivity.class.getSimpleName();
    
    private static final String EXTRA_CARDS = "EXTRA_CARDS";
    //private static final String EXTRA_IMAGE = "EXTRA_IMAGE";

    private final List<CardPresenter> mCardPresenters = new ArrayList<CardPresenter>();
    private CardScrollView mCardScrollView;
    // Not actually used?
    //private Bitmap mImage;

    public static Intent newIntent(Context context,
            List<CardPresenter> cardResults, Result rawResult, Bitmap bitmap) {

        Intent intent = new Intent(context, ResultsActivity.class);
        
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
        
        if (cardResults != null) {
            intent.putExtra(EXTRA_CARDS,
                    cardResults.toArray(new CardPresenter[cardResults.size()]));
        }
        
        /*
        ByteArrayOutputStream imageStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, imageStream);
        intent.putExtra(EXTRA_IMAGE, imageStream.toByteArray());
        */

        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (savedInstanceState != null) {
            readExtras(intent.getExtras());
        } else if (intent != null && intent.getExtras() != null) {
            readExtras(intent.getExtras());
        } else {
            Log.e(TAG, "No extras were present");
            finish();
            return;
        }

        if (mCardPresenters.size() == 0) {
            Log.w(TAG, "There were no cards to display");
            finish();
            return;
        }

        mCardScrollView = new CardScrollView(this);
        mCardScrollView.setAdapter(new CardScrollViewAdapter(this, mCardPresenters));
        mCardScrollView.activate();
        mCardScrollView.setOnItemClickListener(mOnItemClickListener);

        setContentView(mCardScrollView);
    }

    private void readExtras(Bundle extras) {
        Parcelable[] parcelCardsArray = extras.getParcelableArray(EXTRA_CARDS);
        for (int i = 0; i < parcelCardsArray.length; i++) {
            mCardPresenters.add((CardPresenter) parcelCardsArray[i]);
        }

        /*
        byte[] imageBytes = extras.getByteArray(EXTRA_IMAGE);
        if (imageBytes != null) {
            mImage2 = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        }
        */
    }

    public static class CardScrollViewAdapter extends CardScrollAdapter {

        private final Context mContext;
        private final List<CardPresenter> mCardPresenters;

        public CardScrollViewAdapter(Context context,
                List<CardPresenter> cardPresenter) {
            mContext = context;
            mCardPresenters = cardPresenter;
        }

        @Override
        public int findIdPosition(Object id) {
            return -1;
        }

        @Override
        public int findItemPosition(Object item) {
            return mCardPresenters.indexOf(item);
        }

        @Override
        public int getCount() {
            return mCardPresenters.size();
        }

        @Override
        public Object getItem(int position) {
            return mCardPresenters.get(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CardPresenter cardPresenter = mCardPresenters.get(position);
            return cardPresenter.getCardView(mContext);
        }
    }

    private final OnItemClickListener mOnItemClickListener = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                long id) {
            CardPresenter cardPresenter = mCardPresenters.get(position);
            PendingIntent pendingIntent = cardPresenter.getPendingIntent();
            if (pendingIntent != null) {
                try {
                    pendingIntent.send();
                } catch (CanceledException e) {
                    Log.w(TAG, e.getMessage());
                }
            } else {
                Log.w(TAG, "No PendingIntent attached to card!");
            }
        }
    };
}
