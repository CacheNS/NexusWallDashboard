package com.cachens.nexusdashboard;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class PhotoRepository {
    private static final int CITY_CACHE_SIZE = 100;

    private final Resources resources;
    private final File rootDirectory;
    private final File userDirectory;
    private final File cityDirectory;
    private final int[] fallbackPhotos = {
            R.drawable.novi_sad_petrovaradin,
            R.drawable.novi_sad_freedom_square,
            R.drawable.novi_sad_dunavska
    };
    private File[] userPhotos = new File[0];
    private File[] cityPhotos = new File[0];

    PhotoRepository(Context context) {
        resources = context.getResources();
        rootDirectory = new File(context.getFilesDir(), "photos");
        userDirectory = new File(rootDirectory, "user");
        cityDirectory = new File(rootDirectory, "novi-sad");
        rootDirectory.mkdirs();
        userDirectory.mkdirs();
        recoverInterruptedCityCache();
        migrateLegacyUserPhotos();
        reload();
    }

    synchronized void reload() {
        userPhotos = imageFiles(userDirectory);
        cityPhotos = imageFiles(cityDirectory);
    }

    private static File[] imageFiles(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return new File[0];
        }
        List<File> images = new ArrayList<File>();
        for (File file : files) {
            String name = file.getName().toLowerCase(java.util.Locale.US);
            if (file.isFile() && (name.startsWith("photo_") || name.endsWith(".jpg")
                    || name.endsWith(".jpeg") || name.endsWith(".png"))) {
                images.add(file);
            }
        }
        File[] result = images.toArray(new File[images.size()]);
        Arrays.sort(result, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return left.getName().compareTo(right.getName());
            }
        });
        return result;
    }

    synchronized Bitmap loadForToday(int targetWidth, int targetHeight) {
        long dayIndex = currentDayIndex();
        for (int attempts = 0; attempts < userPhotos.length; attempts++) {
            int index = positiveModulo(dayIndex + attempts, userPhotos.length);
            File file = userPhotos[index];
            Bitmap bitmap = decode(file, targetWidth, targetHeight);
            if (bitmap != null) {
                return bitmap;
            }
        }
        for (int attempts = 0; attempts < cityPhotos.length; attempts++) {
            int index = positiveModulo(dayIndex + attempts, cityPhotos.length);
            File file = cityPhotos[index];
            Bitmap bitmap = decode(file, targetWidth, targetHeight);
            if (bitmap != null) {
                return bitmap;
            }
        }
        int resourceId = fallbackPhotos[positiveModulo(dayIndex, fallbackPhotos.length)];
        return BitmapFactory.decodeResource(resources, resourceId);
    }

    private static long currentDayIndex() {
        Calendar calendar = Calendar.getInstance();
        long previousYear = calendar.get(Calendar.YEAR) - 1L;
        return previousYear * 365L + previousYear / 4L - previousYear / 100L
                + previousYear / 400L + calendar.get(Calendar.DAY_OF_YEAR);
    }

    private static int positiveModulo(long value, int divisor) {
        return (int) ((value % divisor + divisor) % divisor);
    }

    File directory() {
        return userDirectory;
    }

    synchronized boolean importStagedCityCache(File stagingDirectory)
            throws IOException, JSONException {
        File manifestFile = new File(stagingDirectory, "manifest.json");
        if (!manifestFile.isFile()) {
            return false;
        }
        JSONObject manifest = new JSONObject(readText(manifestFile));
        JSONArray photos = manifest.getJSONArray("photos");
        if (photos.length() != CITY_CACHE_SIZE) {
            throw new IOException("Expected " + CITY_CACHE_SIZE + " city photos, found " + photos.length());
        }

        File temporary = new File(rootDirectory, "novi-sad.new");
        File backup = new File(rootDirectory, "novi-sad.old");
        deleteTree(temporary);
        deleteTree(backup);
        if (!temporary.mkdirs()) {
            throw new IOException("Could not create temporary city photo directory");
        }

        try {
            Set<String> filenames = new HashSet<String>();
            for (int i = 0; i < photos.length(); i++) {
                JSONObject entry = photos.getJSONObject(i);
                String filename = entry.getString("filename");
                if (!filename.matches("photo_[0-9]{3}\\.jpg")) {
                    throw new IOException("Invalid city photo filename: " + filename);
                }
                if (!filenames.add(filename)) {
                    throw new IOException("Duplicate city photo filename: " + filename);
                }
                File source = new File(stagingDirectory, filename);
                if (!source.isFile() || !entry.getString("sha256").equalsIgnoreCase(sha256(source))) {
                    throw new IOException("City photo validation failed: " + filename);
                }
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
                if (bounds.outWidth <= bounds.outHeight || bounds.outHeight <= 0) {
                    throw new IOException("City photo is not a valid landscape image: " + filename);
                }
                copy(source, new File(temporary, filename));
            }
            for (int i = 1; i <= CITY_CACHE_SIZE; i++) {
                String expected = String.format(java.util.Locale.US, "photo_%03d.jpg", i);
                if (!filenames.contains(expected)) {
                    throw new IOException("Missing city photo manifest entry: " + expected);
                }
            }
            if (imageFiles(temporary).length != CITY_CACHE_SIZE) {
                throw new IOException("Temporary city cache is incomplete");
            }
            copy(manifestFile, new File(temporary, "manifest.json"));
            File attributions = new File(stagingDirectory, "ATTRIBUTIONS.txt");
            if (attributions.isFile()) {
                copy(attributions, new File(temporary, "ATTRIBUTIONS.txt"));
            }

            if (cityDirectory.exists() && !cityDirectory.renameTo(backup)) {
                throw new IOException("Could not preserve the existing city cache");
            }
            if (!temporary.renameTo(cityDirectory)) {
                if (backup.exists()) {
                    backup.renameTo(cityDirectory);
                }
                throw new IOException("Could not activate the new city cache");
            }
            deleteTree(backup);
            deleteTree(stagingDirectory);
            reload();
            return true;
        } catch (IOException error) {
            deleteTree(temporary);
            throw error;
        } catch (JSONException error) {
            deleteTree(temporary);
            throw error;
        }
    }

    synchronized int cityPhotoCount() {
        return cityPhotos.length;
    }

    private void recoverInterruptedCityCache() {
        File backup = new File(rootDirectory, "novi-sad.old");
        if (!cityDirectory.exists() && backup.isDirectory() && !backup.renameTo(cityDirectory)) {
            Log.w("NexusDashboard", "Could not restore interrupted Novi Sad photo cache");
        }
    }

    private void migrateLegacyUserPhotos() {
        File[] legacy = rootDirectory.listFiles();
        if (legacy == null) {
            return;
        }
        for (File file : legacy) {
            if (file.isFile() && file.getName().startsWith("photo_")) {
                file.renameTo(new File(userDirectory, file.getName()));
            }
        }
    }

    private static String readText(File file) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), "UTF-8"));
        try {
            StringBuilder result = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                result.append(buffer, 0, read);
            }
            return result.toString();
        } finally {
            reader.close();
        }
    }

    private static void copy(File source, File destination) throws IOException {
        BufferedInputStream input = new BufferedInputStream(new FileInputStream(source));
        try {
            BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(destination));
            try {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            BufferedInputStream input = new BufferedInputStream(new FileInputStream(file));
            try {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            } finally {
                input.close();
            }
            StringBuilder value = new StringBuilder();
            for (byte item : digest.digest()) {
                value.append(String.format(java.util.Locale.US, "%02x", item & 0xff));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("SHA-256 is unavailable", error);
        }
    }

    private static void deleteTree(File file) throws IOException {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteTree(child);
                }
            }
        }
        if (!file.delete()) {
            throw new IOException("Could not delete " + file.getAbsolutePath());
        }
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
