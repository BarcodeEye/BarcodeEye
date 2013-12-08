package com.github.barcodeeye.scan.result.internal;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.github.barcodeeye.scan.api.CardPresenter;
import com.github.barcodeeye.scan.result.ResultProcessor;
import com.google.zxing.Result;
import com.google.zxing.client.result.TextParsedResult;

public class TextResultProcessor extends ResultProcessor<TextParsedResult> {

    private static final String TAG = TextResultProcessor.class.getSimpleName();

    public TextResultProcessor(Context context, TextParsedResult parsedResult,
            Result result, Uri photoUri) {
        super(context, parsedResult, result, photoUri);
    }

    @Override
    public List<CardPresenter> getCardResults() {
        List<CardPresenter> cardPresenters = new ArrayList<CardPresenter>();

        TextParsedResult parsedResult = getParsedResult();
        String text = parsedResult.getText();
        try {
            Uri uri = Uri.parse("http://www.google.com/#q="
                    + URLEncoder.encode(text, "UTF-8"));
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);

            CardPresenter cardPresenter = new CardPresenter();
            cardPresenter.setText(text).setFooter("Serch Web");
            cardPresenter.setPendingIntent(createPendingIntent(getContext(),
                    intent));
            cardPresenter.addImage(getPhotoUri());

            cardPresenters.add(cardPresenter);

        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, e.getMessage(), e);
        }

        return cardPresenters;
    }

}
