package com.github.barcodeeye.scan.api;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.PendingIntent;
import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;

import com.google.android.glass.app.Card;

public class CardPresenter implements Parcelable {

    private static final String TAG = CardPresenter.class.getSimpleName();
    private final List<Uri> mImages = new ArrayList<Uri>();
    private String mText;
    private String mFooter;
    private PendingIntent mPendingIntent;

    public CardPresenter() {
    }

    public CardPresenter(String text, String footer, PendingIntent intent,
            List<Uri> images) {
        mText = text;
        mFooter = footer;
        mPendingIntent = intent;
        if (images != null) {
            mImages.addAll(images);
        }
    }

    public String getText() {
        return mText;
    }

    public CardPresenter setText(String text) {
        mText = text;
        return this;
    }

    public String getFooter() {
        return mFooter;
    }

    public CardPresenter setFooter(String footer) {
        mFooter = footer;
        return this;
    }

    public PendingIntent getPendingIntent() {
        return mPendingIntent;
    }

    public CardPresenter setPendingIntent(PendingIntent pendingIntent) {
        mPendingIntent = pendingIntent;
        return this;
    }

    public List<Uri> getImages() {
        return mImages;
    }

    public CardPresenter addImage(Context context, int resourceId) {
        String packageName = context.getPackageName();
        return addImage(Uri.parse(
                "android.resource://" + packageName + "/" + resourceId));
    }

    public CardPresenter addImage(Uri uri) {
        if (uri != null) {
            mImages.add(uri);
        } else {
            Log.w(TAG, "PhotoUri was null!");
        }
        return this;
    }

    public View getCardView(Context context) {
        Card card = new Card(context);
        card.setText(mText);
        card.setFootnote(mFooter);
        for (Uri uri : mImages) {
            if (uri != null) {
                Log.w(TAG, "ImageUri: " + uri.toString());
                try {
                    card.addImage(MediaStore.Images.Media.getBitmap(
                            context.getContentResolver(), uri));
                } catch (IOException e) {
                    Log.e(TAG, "Failed to load: " + e);
                }
            } else {
                Log.w(TAG, "We got a null imageUri!");
            }
        }

        return card.getView();
    }

    /* *********************************************************************
     * Parcelable interface related methods
     */
    protected CardPresenter(Parcel in) {
        in.readList(mImages, Uri.class.getClassLoader());
        mText = in.readString();
        mFooter = in.readString();
        mPendingIntent = in.readParcelable(PendingIntent.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeList(mImages);
        dest.writeString(mText);
        dest.writeString(mFooter);
        dest.writeParcelable(mPendingIntent, 0);
    }

    public static final Parcelable.Creator<CardPresenter> CREATOR = new Parcelable.Creator<CardPresenter>() {
        @Override
        public CardPresenter createFromParcel(Parcel in) {
            return new CardPresenter(in);
        }

        @Override
        public CardPresenter[] newArray(int size) {
            return new CardPresenter[size];
        }
    };
}
