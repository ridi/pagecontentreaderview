package com.ridi.books.viewer.reader.pagecontent;

import android.graphics.Bitmap;
import android.support.annotation.WorkerThread;

public interface BitmapPostProcessor {
    @WorkerThread
    Bitmap process(Bitmap src);
}
