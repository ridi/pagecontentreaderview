package com.ridi.books.viewer.reader.pagecontent;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

public class DoublePageContent implements PageContent {
    private final PageContent leftPage;
    private final PageContent rightPage;
    private final SizeF size;

    DoublePageContent(PageContent leftPage, PageContent rightPage,
                      DoublePageSizePolicy sizePolicy) {
        this.leftPage = leftPage;
        this.rightPage = rightPage;
        this.size = sizePolicy.computeSize(leftPage.getSize(), rightPage.getSize());
    }

    @Override
    public SizeF getSize() {
        return size;
    }

    @Override
    public Bitmap renderToBitmap(int bitmapWidth, int bitmapHeight, int startX, int startY,
                                 int pageWidth, int pageHeight, boolean forHighQuality) {
        SizeF leftSize = leftPage.getSize();
        SizeF rightSize = rightPage.getSize();

        Bitmap leftBitmap = null, rightBitmap = null;

        int leftPageWidth = (int) ((float) pageWidth * leftSize.width / size.width);
        int leftBmWidth = Math.min(bitmapWidth, leftPageWidth - (-startX));
        int leftPageHeight = (int) ((float) pageHeight * leftSize.height / size.height);
        int leftBmHeight = bitmapHeight;

        // 필요한만큼만 확보
        if (-startY + bitmapHeight > leftPageHeight) {
            leftBmHeight = leftPageHeight - (-startY);
        }
        
        if (-startX < leftPageWidth && leftBmHeight > 0) {  // 왼쪽 페이지가 영역에 포함됨
            leftBitmap = leftPage.renderToBitmap(leftBmWidth, leftBmHeight,
                    startX, startY, leftPageWidth, leftPageHeight, forHighQuality);
        } else {
            leftBmWidth = 0;
        }

        int rightPageWidth = (int) ((float) pageWidth * rightSize.width / size.width);
        int rightBmWidth = Math.min(bitmapWidth - leftBmWidth, rightPageWidth);
        int rightPageHeight = (int) ((float) pageHeight * rightSize.height / size.height);
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

        Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        if (leftBitmap != null) {
            canvas.drawBitmap(leftBitmap, null,
                    new Rect(0, 0,
                            leftBitmap.getWidth(), leftBitmap.getHeight()), null);
            leftBitmap.recycle();
        }
        if (rightBitmap != null) {
            int left = leftBitmap != null ? leftBitmap.getWidth()
                    : bitmapWidth - rightBitmap.getWidth();
            canvas.drawBitmap(rightBitmap, null,
                    new Rect(left, 0, left + rightBitmap.getWidth(), rightBitmap.getHeight()), null);
            rightBitmap.recycle();
        }

        return bitmap;
    }

    @Override
    public List<Link> getLinkList() {
        List<Link> linkList = new ArrayList<>();
        linkList.addAll(leftPage.getLinkList());
        linkList.addAll(
                horizontalOffsetLinkList(
                        rightPage.getLinkList(),
                        size.width / 2
                )
        );
        return linkList;
    }

    private List<Link> horizontalOffsetLinkList(List<Link> linkList, float offsetX) {
        List<Link> copiedList = new ArrayList<>(linkList);
        for (Link link : copiedList) {
            RectF rect = new RectF(link.getBoundingRect());
            rect.offset(offsetX, 0f);
            link.setBoundingRect(rect);
        }
        return copiedList;
    }
}
