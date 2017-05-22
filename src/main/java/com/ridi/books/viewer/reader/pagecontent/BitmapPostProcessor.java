package com.ridi.books.viewer.reader.pagecontent;

import android.graphics.Bitmap;
import android.support.annotation.WorkerThread;

/**
 * Created by kering on 2017. 5. 12..
 */
public interface BitmapPostProcessor {
    @WorkerThread
    Bitmap process(Bitmap src);
}
