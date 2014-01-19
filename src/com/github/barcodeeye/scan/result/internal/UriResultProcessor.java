package com.github.barcodeeye.scan.result.internal;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.github.barcodeeye.scan.api.CardPresenter;
import com.github.barcodeeye.scan.result.ResultProcessor;
import com.google.zxing.Result;
import com.google.zxing.client.result.ParsedResult;
import com.google.zxing.client.result.URIParsedResult;

public class UriResultProcessor extends ResultProcessor {

    public UriResultProcessor(Context context, ParsedResult result,
            Result rawResult/*, Uri photoUri*/) {
        super(context, result, rawResult);//, photoUri);
    }

    @Override
    public List<CardPresenter> getCardResults() {
        List<CardPresenter> cardResults = new ArrayList<CardPresenter>();

        URIParsedResult result = (URIParsedResult) getResult();

        CardPresenter cardPresenter = new CardPresenter()
                .setText("Open in Browser")
                .setFooter(result.getDisplayResult());

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(result.getURI()));
        cardPresenter.setPendingIntent(createPendingIntent(getContext(), intent));

        cardResults.add(cardPresenter);

        return cardResults;
    }

}
