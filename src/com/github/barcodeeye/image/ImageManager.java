package com.github.barcodeeye.image;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

public class ImageManager {

    private static final String TAG = ImageManager.class.getSimpleName();
    private static final String PHOTO_DIR = "BarcodeEye";
    private final File mDir;

    public ImageManager(Context context) {
        File publicDirectory = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        mDir = new File(publicDirectory.getAbsolutePath() + File.separator + PHOTO_DIR);
        if (mDir.mkdirs() || mDir.isDirectory()) {
            // good!
        } else {
            Log.e(TAG, "Unable to create photo directory! " + mDir.toString());
        }
    }

    public Bitmap getImage(Uri imageUri) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(imageUri.toString(), options);
    }

    public Uri saveImage(String name, Bitmap bitmap) throws IOException {
        FileOutputStream out = new FileOutputStream(getImageAbsolutePath(name));
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        out.close();
        return getImageUri(name);
    }

    public String getImageAbsolutePath(String name) {
        return mDir.getAbsolutePath() + File.separator + name;
    }

    public Uri getImageUri(String name) {
        return Uri.fromFile(new File(getImageAbsolutePath(name)));
    }
}
