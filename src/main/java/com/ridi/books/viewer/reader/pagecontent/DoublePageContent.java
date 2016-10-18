package com.ridi.books.viewer.reader.pagecontent;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.support.annotation.WorkerThread;

public class DoublePageContent implements PageContent {
    private PageContent leftPage;
    private PageContent rightPage;

    @WorkerThread
    public static PageContent getPageContent(PageContentProvider singleProvider,
                                             int leftIndex, int rightIndex, boolean useDummyContent) {
        PageContent leftPage = null, rightPage = null;
        
        if (leftIndex >= 0 && leftIndex < singleProvider.getPageContentCount()) {
            leftPage = singleProvider.getPageContent(leftIndex);
            if (leftPage == null) {
                return null;
            }
        }
        
        if (rightIndex >= 0 && rightIndex < singleProvider.getPageContentCount()) {
            rightPage = singleProvider.getPageContent(rightIndex);
            if (rightPage == null) {
                return null;
            }
        }
        
        if (leftPage == null && rightPage == null) {
            return null;
        } else if (leftPage == null) {
            if (useDummyContent) {
                leftPage = new DummyPageContent(rightPage);
            } else {
                return rightPage;
            }
        } else if (rightPage == null) {
            if (useDummyContent) {
                rightPage = new DummyPageContent(leftPage);
            } else {
                return leftPage;
            }
        }

        return new DoublePageContent(leftPage, rightPage);
    }
    
    private DoublePageContent(PageContent leftPage, PageContent rightPage) {
        this.leftPage = leftPage;
        this.rightPage = rightPage;
    }

    @Override
    public float getWidth() {
        return Math.max(leftPage.getWidth(), rightPage.getWidth()) * 2;
    }

    @Override
    public float getHeight() {
        return Math.max(leftPage.getHeight(), rightPage.getHeight());
    }

    @Override
    public Bitmap renderToBitmap(int bitmapWidth, int bitmapHeight, int startX, int startY,
                                 int pageWidth, int pageHeight, boolean forHighQuality) {
        Bitmap leftBitmap = null, rightBitmap = null;

        int leftPageWidth = (int)(pageWidth * leftPage.getWidth() / getWidth());
        int leftBmWidth = Math.min(bitmapWidth, leftPageWidth - (-startX));
        int leftPageHeight = (int)(pageHeight * leftPage.getHeight() / getHeight());
        int leftBmHeight = bitmapHeight;

        // 필요한만큼만 확보
        if (-startY + bitmapHeight > leftPageHeight) {
            leftBmHeight = leftPageHeight - (-startY);
        }
        
        if (-startX < leftPageWidth && leftBmHeight > 0) {  // 왼쪽 페이지가 영역에 포함됨
            leftBitmap = leftPage.renderToBitmap(leftBmWidth, leftBmHeight,
                    startX, startY, leftPageWidth, leftPageHeight, forHighQuality);
        }
        
        if (rightPage == null) {
            return leftBitmap;
        }

        int rightPageWidth = (int)(pageWidth * rightPage.getWidth() / getWidth());
        int rightBmWidth = Math.min(bitmapWidth - leftBmWidth, rightPageWidth);
        int rightPageHeight = (int)(pageHeight * rightPage.getHeight() / getHeight());
        int rightBmHeight = bitmapHeight;

        // 필요한만큼만 확보
        if (-startY + bitmapHeight > rightPageHeight) {
            rightBmHeight = rightPageHeight - (-startY);
        }

        if (-startX + bitmapWidth >= leftPageWidth && rightBmHeight > 0) {   // 오른쪽 페이지가 영역에 포함됨
            rightBitmap = rightPage.renderToBitmap(rightBmWidth, rightBmHeight,
                                                   Math.min(0, leftPageWidth - (-startX)), startY,
                                                   rightPageWidth, rightPageHeight, forHighQuality);
        }

        if (leftBitmap == null && rightBitmap == null) {
            return null;
        }

        Bitmap bitmap = Bitmap.createBitmap(
                bitmapWidth, bitmapHeight, getPreferredBitmapConfig(leftBitmap, rightBitmap));

        Canvas canvas = new Canvas(bitmap);
        if (leftBitmap != null) {
            canvas.drawBitmap(leftBitmap, null,
                    new Rect(0, 0,
                            leftBitmap.getWidth(), leftBitmap.getHeight()),
                    null);
            leftBitmap.recycle();
        }
        if (rightBitmap != null) {
            int left = leftBitmap != null ? leftBitmap.getWidth() : rightBitmap.getWidth();
            canvas.drawBitmap(rightBitmap, null,
                    new Rect(left, 0, left + rightBitmap.getWidth(), rightBitmap.getHeight()), null);
            rightBitmap.recycle();
        }

        return bitmap;
    }

    private Bitmap.Config getPreferredBitmapConfig(Bitmap leftBitmap, Bitmap rightBitmap) {
        int leftBitmapConfigOrdinal = leftBitmap != null ? leftBitmap.getConfig().ordinal() : -1;
        int rightBitmapConfigOrdinal = rightBitmap != null ? rightBitmap.getConfig().ordinal() : -1;
        int preferredBitmapConfigOrdinal = Math.max(leftBitmapConfigOrdinal, rightBitmapConfigOrdinal);
        return Bitmap.Config.values()[preferredBitmapConfigOrdinal];
    }
}
