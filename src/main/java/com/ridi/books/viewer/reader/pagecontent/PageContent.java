package com.ridi.books.viewer.reader.pagecontent;

import android.graphics.Bitmap;
import android.support.annotation.WorkerThread;

public interface PageContent {
    SizeF getSize();
    @WorkerThread
    Bitmap renderToBitmap(int bitmapWidth, int bitmapHeight, int startX,
                          int startY, int pageWidth, int pageHeight, boolean forHighQuality);
}
