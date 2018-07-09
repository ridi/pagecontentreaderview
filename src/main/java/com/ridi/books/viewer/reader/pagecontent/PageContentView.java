package com.ridi.books.viewer.reader.pagecontent;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.support.annotation.ColorInt;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.ImageView;

class PageContentView extends ViewGroup {
    static final int NO_INDEX = Integer.MIN_VALUE;

    private int index;
    private Size canvasSize;
    @ColorInt private int paperColor;
    private FitPolicy fitPolicy;
    private BackgroundTaskListener backgroundTaskListener;

    private Size size;
    
    private PageContent pageContent;
    private AsyncTask<Void, Void, PageContent> contentLoadTask;

    private PageContentImageView fullView;
    private AsyncTask<Void, Void, Bitmap> fullRenderingTask;
    private PageContentImageView hqView;  // high quality view
    private AsyncTask<HighQualityInfo, Void, HighQualityInfo> hqRenderingTask;
    private HighQualityInfo hqInfo;
    private BitmapPostProcessor postProcessor;
    
    private boolean rendered;

    PageContentView(Context context, int canvasWidth, int canvasHeight, @ColorInt int paperColor,
                    FitPolicy fitPolicy, BackgroundTaskListener backgroundTaskListener,
                    BitmapPostProcessor postProcessor) {
        this(context, null);
        this.index = NO_INDEX;
        this.canvasSize = new Size(canvasWidth, canvasHeight);
        this.paperColor = paperColor;
        this.fitPolicy = fitPolicy;
        this.backgroundTaskListener = backgroundTaskListener;
        this.postProcessor = postProcessor;

        size = canvasSize;
        fullView = new PageContentImageView(context);
        fullView.setPaperColor(paperColor);
        fullView.setVisibility(INVISIBLE);
        fullView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(fullView);
    }

    private PageContentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void clear() {
        index = NO_INDEX;
        if (contentLoadTask != null) {
            contentLoadTask.cancel(true);
            contentLoadTask = null;
        }
        
        pageContent = null;
        rendered = false;
        
        if (fullRenderingTask != null) {
            fullRenderingTask.cancel(true);
            fullRenderingTask = null;
        }
        
        if (hqRenderingTask != null) {
            hqRenderingTask.cancel(true);
            hqRenderingTask = null;
        }

        size = canvasSize;

        fullView.setImageBitmap(null);
        fullView.setVisibility(INVISIBLE);
        hideHqViewIfExists();
    }
    
    private void onStartBackgroundTask() {
        if (backgroundTaskListener != null) {
            backgroundTaskListener.onStartBackgroundTask();
        }
    }
    
    private void onCompleteBackgroundTask() {
        if (backgroundTaskListener != null) {
            backgroundTaskListener.onCompleteBackgroundTask();
        }
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width, height;
        
        switch(MeasureSpec.getMode(widthMeasureSpec)) {
            case MeasureSpec.UNSPECIFIED:
                width = size.width;
                break;
            default:
            case MeasureSpec.AT_MOST:
            case MeasureSpec.EXACTLY:
                width = MeasureSpec.getSize(widthMeasureSpec);
        }
        switch(MeasureSpec.getMode(heightMeasureSpec)) {
            case MeasureSpec.UNSPECIFIED:
                height = size.height;
                break;
            default:
            case MeasureSpec.AT_MOST:
            case MeasureSpec.EXACTLY:
                height = MeasureSpec.getSize(heightMeasureSpec);
        }
        
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;

        fullView.layout(0, 0, width, height);

        if (hqInfo != null) {
            if (hqInfo.size.width != width || hqInfo.size.height != height) {
                // Zoomed since patch was created
                hqInfo = null;
                hideHqViewIfExists();
            } else {
                hqView.layout(hqInfo.area.left, hqInfo.area.top, hqInfo.area.right, hqInfo.area.bottom);
            }
        }
    }
    
    void loadPageContent(final PageContentProvider provider, final int index) {
        clear();

        this.index = index;
        contentLoadTask = new AsyncTask<Void, Void, PageContent>() {
            @Override
            protected void onPreExecute() {
                onStartBackgroundTask();
            }
            
            @Override
            protected PageContent doInBackground(Void... params) {
                return provider.getPageContent(index);
            }
            
            @Override
            protected void onPostExecute(PageContent result) {
                onCompleteBackgroundTask();
                setPageContent(result);
            }
            
            @Override
            protected void onCancelled() {
                onCompleteBackgroundTask();
            }
        };
        
        contentLoadTask.execute();
    }
    
    private void setPageContent(PageContent pageContent) {
        if (pageContent == null) {
            return;
        }
        
        if (fullRenderingTask != null) {
            fullRenderingTask.cancel(true);
            fullRenderingTask = null;
        }
        
        this.pageContent = pageContent;
        // Calculate scaled size that fits within the screen limits
        // This is the size at minimum zoom
        SizeF contentSize = pageContent.getSize();
        float scale = fitPolicy.calculateScale(canvasSize.width, canvasSize.height, contentSize);
        size = new Size((int) (contentSize.width * scale), (int) (contentSize.height * scale));
        
        // Render the page in the background
        fullRenderingTask = new AsyncRenderingTask<Void, Void, Bitmap>() {
            @Override
            protected void onPreExecute() {
                onStartBackgroundTask();
            }

            @Override
            protected Bitmap doInBackground(Void... params) {
                PageContent pageContent = PageContentView.this.pageContent;

                if (pageContent != null) {
                    Bitmap bitmap = pageContent.renderToBitmap(
                            size.width, size.height, 0, 0, size.width, size.height, false);
                    return applyPostProcessor(bitmap);
                } else {
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap result) {
                onCompleteBackgroundTask();
                fullView.setImageBitmap(result);
                fullView.setVisibility(VISIBLE);
                rendered = true;
                invalidate();
            }
            
            @Override
            protected void onCancelled() {
                onCompleteBackgroundTask();
            }
        };

        fullRenderingTask.execute();
        
        requestLayout();
    }
    
    void updateHighQuality() {
        Rect viewArea = new Rect(getLeft(), getTop(), getRight(), getBottom());
        
        // If the viewArea's size matches the unzoomed size, there is no need for an hq patch
        if (viewArea.width() != size.width || viewArea.height() != size.height) {
            Size hqSize = new Size(viewArea.width(), viewArea.height());
            Rect hqArea = new Rect(0, 0, canvasSize.width, canvasSize.height);

            // Intersect and test that there is an intersection
            if (!hqArea.intersect(viewArea)) {
                return;
            }

            // Offset patch area to be relative to the view top left
            hqArea.offset(-viewArea.left, -viewArea.top);
            
            boolean areaUnchanged = false;
            if (hqInfo != null) {
                areaUnchanged = hqArea.equals(hqInfo.area) && hqSize.equals(hqInfo.size);
            }
            
            // If being asked for the same area as last time and not because of an update then nothing to do
            if (areaUnchanged) {
                return;
            }

            // Stop the rendering of previous patch if still going
            if (hqRenderingTask != null) {
                hqRenderingTask.cancel(true);
                hqRenderingTask = null;
            }

            // Create and add the image view if not already done
            if (hqView == null) {
                hqView = new PageContentImageView(getContext(), true);
                hqView.setPaperColor(paperColor);
                hqView.setVisibility(INVISIBLE);
                hqView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                addView(hqView);
            }

            hqRenderingTask = new AsyncRenderingTask<HighQualityInfo, Void, HighQualityInfo>() {
                @Override
                protected HighQualityInfo doInBackground(HighQualityInfo... params) {
                    HighQualityInfo info = params[0];

                    if (pageContent != null) {
                        int bitmapWidth = info.area.width();
                        int bitmapHeight = info.area.height();
                        int startX = -info.area.left;
                        int startY = -info.area.top;
                        int pageWidth = info.size.width;
                        int pageHeight = info.size.height;

                        Bitmap bitmap = pageContent.renderToBitmap(bitmapWidth, bitmapHeight,
                                startX, startY, pageWidth, pageHeight, true);
                        info.bitmap = applyPostProcessor(bitmap);
                    }

                    return info;
                }
                
                @Override
                protected void onPostExecute(HighQualityInfo result) {
                    if (hqRenderingTask == this && result.bitmap != null) {
                        hqInfo = result;
                        hqView.setImageBitmap(result.bitmap);
                        hqView.setVisibility(VISIBLE);

                        // Calling requestLayout here doesn't lead to a later call to layout. No idea
                        // why, but apparently others have run into the problem.
                        hqView.layout(hqInfo.area.left, hqInfo.area.top, hqInfo.area.right, hqInfo.area.bottom);
                        invalidate();
                    }
                }
            };
            
            hqRenderingTask.execute(new HighQualityInfo(hqSize, hqArea));
        }
    }
    
    void removeHighQuality() {
        // Stop the rendering of the patch if still going
        if (hqRenderingTask != null) {
            hqRenderingTask.cancel(true);
            hqRenderingTask = null;
        }

        // And get rid of it
        hqInfo = null;
        hideHqViewIfExists();
    }

    private void hideHqViewIfExists() {
        if (hqView != null) {
            hqView.setImageBitmap(null);
            hqView.setVisibility(INVISIBLE);
        }
    }

    int getIndex() {
        return index;
    }
    
    boolean isRendered() {
        return rendered;
    }
    
    @Override
    public boolean isOpaque() {
        return true;
    }

    public static class Size {
        public int width;
        public int height;
        
        public Size(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Size size = (Size) obj;
            return width == size.width && height == size.height;
        }
    }
    
    private static class HighQualityInfo {
        public Bitmap bitmap;
        public Size size;
        public Rect area;
        
        public HighQualityInfo(Size size, Rect area) {
            this.size = size;
            this.area = area;
        }
    }

    private abstract class AsyncRenderingTask<Params, Progress, Result>
            extends AsyncTask<Params, Progress, Result> {
        protected Bitmap applyPostProcessor(Bitmap bitmap) {
            if (!isCancelled() && postProcessor != null) {
                Bitmap processed = postProcessor.process(bitmap);
                if (processed != bitmap) {
                    bitmap.recycle();
                    bitmap = processed;
                }
            }
            return bitmap;
        }
    }
}
