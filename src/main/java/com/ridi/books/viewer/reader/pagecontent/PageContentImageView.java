package com.ridi.books.viewer.reader.pagecontent;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.support.annotation.ColorInt;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

class PageContentImageView extends View {
    private boolean bitmapSet;
    private Bitmap bitmap;
    @ColorInt private int paperColor;
    private ImageView.ScaleType scaleType;
    private Rect rect = new Rect();
    private boolean isHighQualityView;
    private boolean didDrawOnce;

    PageContentImageView(Context context) {
        this(context, null);
    }

    PageContentImageView(Context context, boolean isHighQualityView) {
        this(context, null);
        this.isHighQualityView = isHighQualityView;
    }

    private PageContentImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setPaperColor(@ColorInt int paperColor) {
        this.paperColor = paperColor;
    }

    public void setScaleType(ImageView.ScaleType scaleType) {
        this.scaleType = scaleType;
    }

    public void setImageBitmap(Bitmap bitmap) {
        if (this.bitmap != null) {
            this.bitmap.recycle();
        }
        this.bitmap = bitmap;
        this.bitmapSet = (bitmap != null);
        this.didDrawOnce = false;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (isHighQualityView) {
            rect.set(0, 0, right - left, bottom - top);
        } else {
            rect.set(left, top, right, bottom);
        }

        if (bitmapSet && changed && !didDrawOnce) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawColor(paperColor);

        if (bitmapSet) {
            canvas.drawBitmap(bitmap, null, rect, null);
            didDrawOnce = true;
        }
    }
}
