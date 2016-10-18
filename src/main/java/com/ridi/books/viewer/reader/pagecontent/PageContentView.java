package com.ridi.books.viewer.reader.pagecontent;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.ImageView;

public class PageContentView extends ViewGroup {
    public interface Listener {
        void onStartBackgroundTask();
        void onCompleteBackgroundTask();
    }

    public interface BitmapPostProcessor {
        Bitmap process(Bitmap src);
    }

    public enum ScaleMode {
        AUTO, WIDTH, HEIGHT
    }
    
    private Size canvasSize;
    private ScaleMode scaleMode;
    private Listener listener;

    private Size size;
    
    private PageContent pageContent;
    private AsyncTask<Void, Void, PageContent> contentLoadTask;
    
    private ImageView entireView;
    private AsyncTask<Void, Void, Bitmap> entireRenderingTask;
    private ImageView hqView;  // high quality view
    private AsyncTask<HighQualityInfo, Void, HighQualityInfo> hqRenderingTask;
    private HighQualityInfo hqInfo;
    private BitmapPostProcessor[] postProcessors;
    
    private boolean rendered;

    public PageContentView(Context context, Size canvasSize, ScaleMode scaleMode,
                           Listener listener, BitmapPostProcessor... postProcessors) {
        this(context, null, 0);

        this.canvasSize = canvasSize;
        this.scaleMode = scaleMode;
        this.listener = listener;
        this.postProcessors = postProcessors;

        entireView = new ImageView(context);
        entireView.setBackgroundColor(Color.WHITE);
        entireView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(entireView);
    }

    private PageContentView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    void clear() {
        if (contentLoadTask != null) {
            contentLoadTask.cancel(true);
            contentLoadTask = null;
        }
        
        pageContent = null;
        rendered = false;
        
        if (entireRenderingTask != null) {
            entireRenderingTask.cancel(true);
            entireRenderingTask = null;
        }
        
        if (hqRenderingTask != null) {
            hqRenderingTask.cancel(true);
            hqRenderingTask = null;
        }
        
        if (size == null) {
            size = canvasSize;
        }
        
        entireView.setImageBitmap(null);
        hideHqViewIfExists();
    }
    
    private void onStartBackgroundTask() {
        if (listener != null) {
            listener.onStartBackgroundTask();
        }
    }
    
    private void onCompleteBackgroundTask() {
        if (listener != null) {
            listener.onCompleteBackgroundTask();
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
                width = MeasureSpec.getSize(widthMeasureSpec);
        }
        switch(MeasureSpec.getMode(heightMeasureSpec)) {
            case MeasureSpec.UNSPECIFIED:
                height = size.height;
                break;
            default:
                height = MeasureSpec.getSize(heightMeasureSpec);
        }
        
        setMeasuredDimension(width, height);
        requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;

        entireView.layout(0, 0, width, height);

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
        
        if (entireRenderingTask != null) {
            entireRenderingTask.cancel(true);
            entireRenderingTask = null;
        }
        
        this.pageContent = pageContent;
        
        // Calculate scaled size that fits within the screen limits
        // This is the size at minimum zoom
        float pageWidth = this.pageContent.getWidth();
        float pageHeight = this.pageContent.getHeight();
        
        float scale;
        switch (scaleMode) {
            case WIDTH:
                scale = canvasSize.width / pageWidth;
                break;
            case HEIGHT:
                scale = canvasSize.height / pageHeight;
                break;
            default:
            case AUTO:
                scale = Math.min(canvasSize.width / pageWidth, canvasSize.height / pageHeight);
                break;
        }
        size = new Size((int) (pageWidth * scale), (int) (pageHeight * scale));
        
        // Render the page in the background
        entireRenderingTask = new AsyncRenderingTask<Void, Void, Bitmap>() {
            @Override
            protected void onPreExecute() {
                onStartBackgroundTask();
            }

            @Override
            protected Bitmap doInBackground(Void... params) {
                PageContent pageContent = PageContentView.this.pageContent;

                if (pageContent != null) {
                    Bitmap bitmap = pageContent.renderToBitmap(size.width, size.height,
                            0, 0, size.width, size.height, false);
                    return applyPostProcessors(bitmap);
                } else {
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap result) {
                onCompleteBackgroundTask();
                entireView.setImageBitmap(result);
                rendered = true;
                invalidate();
            }
            
            @Override
            protected void onCancelled() {
                onCompleteBackgroundTask();
            }
        };

        entireRenderingTask.execute();
        
        requestLayout();
    }
    
    void updateHighQuality() {
        Rect viewArea = new Rect(getLeft(), getTop(), getRight(), getBottom());
        
        // If the viewArea's size matches the unzoomed size, there is no need for an hq patch
        if (viewArea.width() != size.width || viewArea.height() != size.height) {
            Size hqSize = new Size(viewArea.width(), viewArea.height());
            Rect hqArea = new Rect(0, 0, canvasSize.width, canvasSize.height);

            // Intersect and test that there is an intersection
            if (!hqArea.intersect(viewArea))
                return;

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
                hqView = new ImageView(getContext());
                hqView.setBackgroundColor(Color.WHITE);
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
                        info.bitmap = applyPostProcessors(bitmap);
                    }

                    return info;
                }
                
                @Override
                protected void onPostExecute(HighQualityInfo result) {
                    if (hqRenderingTask == this) {
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

    private abstract class AsyncRenderingTask<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {
        protected Bitmap applyPostProcessors(Bitmap bitmap) {
            for (BitmapPostProcessor processor : postProcessors) {
                if (bitmap == null) {
                    break;
                }

                Bitmap processed = processor.process(bitmap);
                if (processed != bitmap) {
                    bitmap.recycle();
                    bitmap = processed;
                }
            }
            return bitmap;
        }
    }
}
