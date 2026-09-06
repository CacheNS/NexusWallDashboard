package com.cachens.nexusdashboard;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

final class PhotoRepository {
    private final Resources resources;
    private final File photoDirectory;
    private final int[] fallbackPhotos = {
            R.drawable.novi_sad_petrovaradin,
            R.drawable.novi_sad_freedom_square,
            R.drawable.novi_sad_dunavska
    };
    private File[] photos = new File[0];
    private int nextIndex;
    private int fallbackIndex;

    PhotoRepository(Context context) {
        resources = context.getResources();
        photoDirectory = new File(context.getFilesDir(), "photos");
        photoDirectory.mkdirs();
        reload();
    }

    synchronized void reload() {
        File[] files = photoDirectory.listFiles();
        photos = files == null ? new File[0] : files;
        Arrays.sort(photos, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return left.getName().compareTo(right.getName());
            }
        });
        nextIndex = 0;
    }

    synchronized Bitmap loadNext(int targetWidth, int targetHeight) {
        for (int attempts = 0; attempts < photos.length; attempts++) {
            File file = photos[nextIndex];
            nextIndex = (nextIndex + 1) % photos.length;
            Bitmap bitmap = decode(file, targetWidth, targetHeight);
            if (bitmap != null) {
                return bitmap;
            }
        }
        int resourceId = fallbackPhotos[fallbackIndex];
        fallbackIndex = (fallbackIndex + 1) % fallbackPhotos.length;
        return BitmapFactory.decodeResource(resources, resourceId);
    }

    File directory() {
        return photoDirectory;
    }

    private static Bitmap decode(File file, int targetWidth, int targetHeight) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sampleSize = 1;
        while (bounds.outWidth / (sampleSize * 2) >= targetWidth
                && bounds.outHeight / (sampleSize * 2) >= targetHeight) {
            sampleSize *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }
}
