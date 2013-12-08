package com.github.barcodeeye.scan.result.internal;

import java.util.List;

import android.content.Context;
import android.net.Uri;

import com.github.barcodeeye.scan.api.CardPresenter;
import com.github.barcodeeye.scan.result.ResultProcessor;
import com.google.zxing.Result;
import com.google.zxing.client.result.ProductParsedResult;

public class ProductResultProcessor extends ResultProcessor<ProductParsedResult> {

    public ProductResultProcessor(Context context, ProductParsedResult parsedResult,
            Result result, Uri photoUri) {
        super(context, parsedResult, result, photoUri);
    }

    @Override
    public List<CardPresenter> getCardResults() {
        return null;
    }
}
