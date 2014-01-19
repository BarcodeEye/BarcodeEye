package com.github.barcodeeye.scan.result;

import android.content.Context;
import android.net.Uri;

import com.github.barcodeeye.scan.result.internal.IsbnResultProcessor;
import com.github.barcodeeye.scan.result.internal.ProductResultProcessor;
import com.github.barcodeeye.scan.result.internal.TextResultProcessor;
import com.github.barcodeeye.scan.result.internal.UriResultProcessor;
import com.google.zxing.Result;
import com.google.zxing.client.result.ISBNParsedResult;
import com.google.zxing.client.result.ParsedResult;
import com.google.zxing.client.result.ProductParsedResult;
import com.google.zxing.client.result.ResultParser;
import com.google.zxing.client.result.URIParsedResult;

public final class ResultProcessorFactory {

    public static ResultProcessor makeResultProcessor(
            Context context, Result rawResult/*, Uri photoUri*/) {

        ParsedResult result = ResultParser.parseResult(rawResult);

        switch (result.getType()) {
            case PRODUCT:
                return new ProductResultProcessor(context, result, rawResult);//, photoUri);
            case URI:
                return new UriResultProcessor(context, result, rawResult);//, photoUri);
            case ISBN:
                return new IsbnResultProcessor(context, result, rawResult);//, photoUri);
            case SMS:
            case GEO:
            case TEL:
            case CALENDAR:
            case ADDRESSBOOK:
            case EMAIL_ADDRESS:
            case WIFI:
                // currently unsupported so we let them fall through
            default:
                return new TextResultProcessor(context, result, rawResult);//, photoUri);
        }
    }
}