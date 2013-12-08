package com.github.barcodeeye.scan.result.internal;

import java.util.List;

import android.content.Context;
import android.net.Uri;

import com.github.barcodeeye.scan.api.CardPresenter;
import com.github.barcodeeye.scan.result.ResultProcessor;
import com.google.zxing.Result;
import com.google.zxing.client.result.ISBNParsedResult;

public class IsbnResultProcessor extends ResultProcessor<ISBNParsedResult> {

    public IsbnResultProcessor(Context context, ISBNParsedResult parsedResult,
            Result result, Uri photoUri) {
        super(context, parsedResult, result, photoUri);
    }

    @Override
    public List<CardPresenter> getCardResults() {
        // TODO Auto-generated method stub
        return null;
    }
}
