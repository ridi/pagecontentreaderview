package com.ridi.books.viewer.reader.pagecontent;

import android.graphics.Bitmap;

public interface PageContent {
    float getWidth();
    float getHeight();
    Bitmap renderToBitmap(int bitmapWidth, int bitmapHeight, int startX,
                          int startY, int pageWidth, int pageHeight, boolean forHighQuality);
}
