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

    public static ResultProcessor<? extends ParsedResult> makeResultProcessor(
            Context context, Result result, Uri photoUri) {

        ParsedResult parsedResult = ResultParser.parseResult(result);

        switch (parsedResult.getType()) {
            case PRODUCT:
                return new ProductResultProcessor(context,
                        (ProductParsedResult) parsedResult, result, photoUri);
            case URI:
                return new UriResultProcessor(context,
                        (URIParsedResult) parsedResult, result, photoUri);
            case ISBN:
                return new IsbnResultProcessor(context,
                        (ISBNParsedResult) parsedResult, result, photoUri);
            case SMS:
            case GEO:
            case TEL:
            case CALENDAR:
            case ADDRESSBOOK:
            case EMAIL_ADDRESS:
            case WIFI:
                // currently unsupported so we let them fall through
            default:
                return new TextResultProcessor(context, parsedResult, result, photoUri);
        }
    }
}