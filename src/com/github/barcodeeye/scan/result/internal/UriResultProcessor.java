package com.github.barcodeeye.scan.result.internal;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.github.barcodeeye.scan.api.CardPresenter;
import com.github.barcodeeye.scan.result.ResultProcessor;
import com.google.zxing.Result;
import com.google.zxing.client.result.URIParsedResult;

public class UriResultProcessor extends ResultProcessor<URIParsedResult> {

    public UriResultProcessor(Context context, URIParsedResult parsedResult,
            Result result, Uri photoUri) {
        super(context, parsedResult, result, photoUri);
    }

    @Override
    public List<CardPresenter> getCardResults() {
        List<CardPresenter> cardResults = new ArrayList<CardPresenter>();

        URIParsedResult parsedResult = getParsedResult();

        CardPresenter cardPresenter = new CardPresenter()
                .setText("Open in Browser")
                .setFooter(parsedResult.getDisplayResult());


        if (getPhotoUri() != null) {
            cardPresenter.addImage(getPhotoUri());
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(parsedResult.getURI()));
        cardPresenter.setPendingIntent(createPendingIntent(getContext(), intent));

        cardResults.add(cardPresenter);

        return cardResults;
    }

}
