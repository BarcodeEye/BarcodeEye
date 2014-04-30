package com.github.barcodeeye.scan;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

import com.github.barcodeeye.scan.api.CardPresenter;
import com.google.android.glass.widget.CardScrollAdapter;
import com.google.android.glass.widget.CardScrollView;

public class ResultsActivity extends Activity {

    private static final String TAG = ResultsActivity.class.getSimpleName();
    private static final String EXTRA_CARDS = "EXTRA_CARDS";
    private static final String EXTRA_IMAGE_URI = "EXTRA_IMAGE_URI";

    private final List<CardPresenter> mCardPresenters = new ArrayList<CardPresenter>();
    private CardScrollView mCardScrollView;

    public static Intent newIntent(Context context,
            List<CardPresenter> cardResults) {

        Intent intent = new Intent(context, ResultsActivity.class);
        if (cardResults != null) {
            intent.putExtra(EXTRA_CARDS,
                    cardResults.toArray(new CardPresenter[cardResults.size()]));
        }

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
        mCardScrollView.setAdapter(new CardScrollViewAdapter(this,
                mCardPresenters));
        mCardScrollView.activate();
        mCardScrollView.setOnItemClickListener(mOnItemClickListener);

        setContentView(mCardScrollView);
    }

    private void readExtras(Bundle extras) {
        Parcelable[] parcelCardsArray = extras.getParcelableArray(EXTRA_CARDS);
        for (int i = 0; i < parcelCardsArray.length; i++) {
            mCardPresenters.add((CardPresenter) parcelCardsArray[i]);
        }
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

        @Override
        public int getPosition(Object item) {
            return mCardPresenters.indexOf(item);
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
