package edu.benchmarkandroid.Benchmark.benchmarks.dogsBenchmark;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.util.Random;

public class DogsPicassoTarget implements Target {

    long lastDetectionTime = 0;
    boolean lastDetectionSuccess = false;
    Random random = new Random();

    public long getLastDetectionTime() {
        return lastDetectionTime;
    }

    @Override
    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
        long start = System.currentTimeMillis();
        // Classify image here
        try {
            Thread.sleep(new Random().nextInt(100) + 1);
        } catch (InterruptedException ex) {

        }
        this.lastDetectionTime = System.currentTimeMillis() - start;
        this.lastDetectionSuccess = true;
    }

    @Override
    public void onBitmapFailed(Drawable errorDrawable) {
        this.lastDetectionSuccess = false;
    }

    @Override
    public void onPrepareLoad(Drawable placeHolderDrawable) {

    }

    public boolean getLastDetectionSuccess() {
        return lastDetectionSuccess;
    }

}
