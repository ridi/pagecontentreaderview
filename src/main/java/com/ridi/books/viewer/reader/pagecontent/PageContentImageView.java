package com.ridi.books.viewer.reader.pagecontent;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.support.annotation.ColorInt;
import android.util.AttributeSet;
import android.view.View;

public class PageContentImageView extends View {
    private Bitmap bitmap;
    @ColorInt private int paperColor;
    private Rect rect = new Rect();
    private boolean highQualityView;
    private boolean dirty;

    public PageContentImageView(Context context) {
        this(context, false);
    }

    PageContentImageView(Context context, boolean highQualityView) {
        this(context, null);
        this.highQualityView = highQualityView;
    }

    private PageContentImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setPaperColor(@ColorInt int paperColor) {
        this.paperColor = paperColor;
    }

    public void setImageBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        dirty = true;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (highQualityView) {
            rect.set(0, 0, right - left, bottom - top);
        } else {
            rect.set(left, top, right, bottom);
        }

        if ((bitmap != null) && changed && dirty) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawColor(paperColor);

        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, rect, null);
            dirty = false;
        }
    }
}
