package com.ridi.books.viewer.reader.pagecontent;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Pair;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.Scroller;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class PageContentReaderView extends AdapterView<PageContentViewAdapter>
                           implements GestureDetector.OnGestureListener,
                                      GestureDetector.OnDoubleTapListener,
                                      ScaleGestureDetector.OnScaleGestureListener {
    private static final float MIN_SCALE                        = 1.0f;
    private static final float MAX_SCALE                        = 5.0f;
    private static final float DEFAULT_SCALE                    = MIN_SCALE;

    private static final int DEFAULT_FLING_DISTANCE_THRESHOLD   = 120;
    private static final int DEFAULT_FLING_VELOCITY_THRESHOLD   = 1000;
    private static final int FLING_MARGIN                       = 70;

    private static final int SCROLLBAR_MIN_THUMB_SIZE           = 5;
    private static final int SCROLLBAR_STROKE_WIDTH             = 8;
    private static final int SCROLLBAR_COLOR                    = 0x88808080;
    
    private static final int MOVING_DIAGONALLY                  = 0;
    private static final int MOVING_UP                          = 1;
    private static final int MOVING_RIGHT                       = 2;
    private static final int MOVING_DOWN                        = 3;
    private static final int MOVING_LEFT                        = 4;

    private static final Paint SCROLLBAR_PAINT;
    static {
        SCROLLBAR_PAINT = new Paint();
        SCROLLBAR_PAINT.setColor(SCROLLBAR_COLOR);
        SCROLLBAR_PAINT.setStrokeWidth(SCROLLBAR_STROKE_WIDTH);
    }
    
    public interface Listener {
        void onViewModeChanged();
        boolean onSingleTapUp(MotionEvent e);
        boolean onScrollWithoutScaling(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY);
        void onScrollChanged();
        void onCurrentIndexChanged();
        void onTryOverFirstPage();
        void onTryOverLastPage();
        void onTouchUp();
    }

    private PageContentViewAdapter adapter;
    private DataSetObserver dataSetObserver = new DataSetObserver() {
        @Override
        public void onChanged() {
            refresh();
        }
    };
    private int currentIndex = PageContentView.NO_INDEX;    // Adapter's index for the current view
    private boolean resetLayout;
    private SparseArray<PageContentView> childViews;
    private LinkedList<PageContentView> viewCache;

    private boolean scrollMode;
    private boolean reverseMode;
    private boolean slidingEnabled;

    private Pair<Integer, Integer> touchStartOffset;
    private boolean keepScrollOffsetEnabled;
    private Point keptScrollOffset;
    
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    private int pageGapPixels;
    private float scale;
    private boolean scaling;
    private boolean doubleTapScalingEnabled;
    private float requestedScale = DEFAULT_SCALE;
    private int flingDistanceThreshold = DEFAULT_FLING_DISTANCE_THRESHOLD;
    private int flingVelocityThreshold = DEFAULT_FLING_VELOCITY_THRESHOLD;
    
    private Scroller scroller;
    private int scrollOffsetX;
    private int scrollOffsetY;
    private int scrollerLastX;
    private int scrollerLastY;
    private boolean scrollDisabled;
    private boolean isExternalGestureMode = false;
    
    private boolean userInteracting;
    private boolean sliding;

    private boolean flexibleContentSize;
    
    private Listener listener;
    
    private boolean tryOverFirst;
    private boolean tryOverLast;
    
    public PageContentReaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        gestureDetector = new GestureDetector(context, this);
        scale = DEFAULT_SCALE;
        scaleGestureDetector = new ScaleGestureDetector(context, this);
        scroller = new Scroller(context);
        
        childViews = new SparseArray<>(3);
        viewCache = new LinkedList<>();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        refresh();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setFlingDistanceThreshold(int flingDistanceThreshold) {
        this.flingDistanceThreshold = flingDistanceThreshold;
    }

    public void setFlingVelocityThreshold(int flingVelocityThreshold) {
        this.flingVelocityThreshold = flingVelocityThreshold;
    }

    public void setViewMode(boolean scrollMode, boolean reverseMode, boolean slidingEnabled) {
        this.scrollMode = scrollMode;
        
        this.reverseMode = reverseMode;
        this.slidingEnabled = slidingEnabled;
        
        userInteracting = false;
        requestLayout();
        
        View view = childViews.get(currentIndex);
        if (this.slidingEnabled && !this.scrollMode && view != null) {
            slideViewOntoScreen(view);
        }

        listener.onViewModeChanged();
    }
    
    public boolean isScrollMode() {
        return scrollMode;
    }
    
    public void setScrollMode(boolean scrollMode) {
        setViewMode(scrollMode, reverseMode, slidingEnabled);
    }
    
    public boolean isReverseMode() {
        return reverseMode;
    }
    
    public void setReverseMode(boolean reverseMode) {
        setViewMode(scrollMode, reverseMode, slidingEnabled);
    }
    
    public boolean isSlidingEnabled() {
        return slidingEnabled;
    }
    
    public void setSlindingEnabled(boolean slidingEnabled) {
        setViewMode(scrollMode, reverseMode, slidingEnabled);
    }

    public void setKeepScrollOffsetEnabled(boolean keepScrollOffsetEnabled) {
        this.keepScrollOffsetEnabled = keepScrollOffsetEnabled;
    }

    public void setDoubleTapScalingEnabled(boolean doubleTapScalingEnabled) {
        this.doubleTapScalingEnabled = doubleTapScalingEnabled;
        gestureDetector.setOnDoubleTapListener(doubleTapScalingEnabled ? this : null);
    }

    public void setPageGapPixels(int pageGapPixels) {
        this.pageGapPixels = pageGapPixels;
        requestLayout();
    }

    public void setCurrentIndex(int index) {
        if (index >= 0 && index < adapter.getCount()) {
            currentIndex = index;
            listener.onCurrentIndexChanged();
            scale = DEFAULT_SCALE;
            resetLayout = true;
            requestLayout();
        }
    }
    
    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setFlexibleContentSize(boolean flexibleContentSize) {
        this.flexibleContentSize = flexibleContentSize;
    }

    private boolean shouldKeepScrollOffset() {
        return keepScrollOffsetEnabled && scale > MIN_SCALE;
    }

    private boolean isLeftOrUpIndexAvailable() {
        return isLeftOrUpIndexAvailable(currentIndex);
    }

    private boolean isLeftOrUpIndexAvailable(int index) {
        return (reverseMode && index + 1 < adapter.getCount()) || (!reverseMode && index > 0);
    }

    private boolean isRightOrDownIndexAvailable() {
        return isRightOrDownIndexAvailable(currentIndex);
    }

    private boolean isRightOrDownIndexAvailable(int index) {
        return (reverseMode && index > 0) || (!reverseMode && index + 1 < adapter.getCount());
    }

    private void setCurrentIndexToLeftOrUp() {
        if (reverseMode) {
            increaseCurrentIndex();
        } else {
            decreaseCurrentIndex();
        }
    }

    private void setCurrentIndexToRightOrDown() {
        if (reverseMode) {
            decreaseCurrentIndex();
        } else {
            increaseCurrentIndex();
        }
    }

    private void increaseCurrentIndex() {
        if (currentIndex + 1 < adapter.getCount()) {
            currentIndex++;
            listener.onCurrentIndexChanged();
        }
    }

    private void decreaseCurrentIndex() {
        if (currentIndex > 0) {
            currentIndex--;
            listener.onCurrentIndexChanged();
        }
    }
    
    private void postSettle(final PageContentView view) {
        post(new Runnable() {
            @Override
            public void run() {
                view.updateHighQuality();
            }
        });
    }
    
    private void postUnsettle(final PageContentView view) {
        post(new Runnable() {
            @Override
            public void run() {
                view.removeHighQuality();
            }
        });
    }
    
    private Point subScreenSizeOffset(PageContentView view) {
        int x = Math.max((getWidth() - view.getMeasuredWidth()) / 2, 0);
        if (scrollMode) {
            if ((reverseMode && view.getIndex() == adapter.getCount() - 1)
                    || (!reverseMode && view.getIndex() == 0)) {
                return new Point(x, 0);
            } else if ((reverseMode && view.getIndex() == 0)
                    || (!reverseMode && view.getIndex() == adapter.getCount() - 1)) {
                return new Point(x, getHeight() - view.getMeasuredHeight());
            }
        }
        return new Point(x, Math.max((getHeight() - view.getMeasuredHeight()) / 2, 0));
    }
    
    private View getCached() {
        if (viewCache.isEmpty()) {
            return null;
        } else {
            return viewCache.removeFirst();
        }
    }

    private PageContentView getOrCreateChild(int index) {
        PageContentView view = childViews.get(index);
        if (view == null) {
            view = (PageContentView) adapter.getView(index, getCached(), this);
            addAndMeasureChild(index, view);
        }

        return view;
    }
    
    private void addAndMeasureChild(int index, PageContentView view) {
        LayoutParams params = view.getLayoutParams();
        if (params == null) {
            params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        }
        addViewInLayout(view, 0, params, true);
        childViews.append(index, view); // Record the view against it's adapter index
        measureView(view);
    }
    
    private void measureView(View view) {
        // See what size the view wants to be
        view.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        view.measure(MeasureSpec.EXACTLY | (int) (view.getMeasuredWidth() * scale),
                MeasureSpec.EXACTLY | (int) (view.getMeasuredHeight() * scale));
    }
    
    private Rect getScrollBounds(int left, int top, int right, int bottom) {
        int xMin = getWidth() - right;
        int xMax = -left;
        int yMin = getHeight() - bottom;
        int yMax = -top;

        // In either dimension, if view smaller than screen then
        // constrain it to be central
        if (xMin > xMax) {
            xMin = xMax = (xMin + xMax) / 2;
        }
        if (yMin > yMax) {
            if (scrollMode) {
                yMin = yMax = 0;
            } else {
                yMin = yMax = (yMin + yMax) / 2;
            }
        }

        return new Rect(xMin, yMin, xMax, yMax);
    }

    private Rect getScrollBounds(View view) {
        // There can be scroll amounts not yet accounted for in
        // onLayout, so add mXScroll and mYScroll to the current
        // positions when calculating the bounds.
        return getScrollBounds(view.getLeft() + scrollOffsetX,
                               view.getTop() + scrollOffsetY,
                               view.getLeft() + view.getMeasuredWidth() + scrollOffsetX,
                               view.getTop() + view.getMeasuredHeight() + scrollOffsetY);
    }
    
    private Point getCorrection(Rect bounds) {
        return new Point(Math.min(Math.max(0, bounds.left), bounds.right),
                         Math.min(Math.max(0, bounds.top), bounds.bottom));
    }
    
    private Runnable scrollProcessor = new Runnable() {
        @Override
        public void run() {
            if (!scroller.isFinished()) {
                scroller.computeScrollOffset();
                int x = scroller.getCurrX();
                int y = scroller.getCurrY();
                scrollOffsetX += x - scrollerLastX;
                scrollOffsetY += y - scrollerLastY;
                scrollerLastX = x;
                scrollerLastY = y;
                listener.onScrollChanged();
                requestLayout();
                post(this);
            } else {
                sliding = false;
                
                if (scrollMode) {
                    settleOrUnsettleViews();
                } else if (!userInteracting) {
                    scaling = false;
                    
                    // End of an inertial scroll and the user is not interacting.
                    // The layout is stable
                    PageContentView cv = null;
                    
                    for (int i = 0; i < childViews.size(); i++) {
                        int index = childViews.keyAt(i);
                        PageContentView view = childViews.get(index);
                        
                        if (index == currentIndex) {
                            cv = view;
                        } else {
                            postUnsettle(view);
                        }
                    }
                    
                    if (cv != null) {
                        postSettle(cv);
                    }
                }
                
                if (tryOverFirst) {
                    listener.onTryOverFirstPage();
                } else if (tryOverLast) {
                    listener.onTryOverLastPage();
                }
                tryOverFirst = tryOverLast = false;
            }
        }
    };
    
    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        drawScrollBars(canvas);
    }
    
    private void drawScrollBars(Canvas canvas) {
        PageContentView view = childViews.get(currentIndex);
        if (view == null || view.getMeasuredWidth() == 0 || view.getMeasuredHeight() == 0) {
            return;
        }

        int count = adapter.getCount();
        if (scrollMode && count > 1) {
            long current = -view.getTop(), total = 0L;
            if (flexibleContentSize) {
                for (int i = 0; i < count; i++) {
                    SizeF contentSize = adapter.getPageContentSize(i);
                    float scale = adapter.getFitPolicy().calculateScale(getWidth(), getHeight(), contentSize);
                    float height = contentSize.height * scale * this.scale + pageGapPixels * this.scale;
                    total += height;
                    if (i < currentIndex) {
                        current += height;
                    }
                }
            } else {
                int prevCount = reverseMode ? count - currentIndex - 1 : currentIndex;
                Point offset =  subScreenSizeOffset(view);
                total = (int) (offset.y * 2 + view.getMeasuredHeight() * count + pageGapPixels * scale * (count - 1));
                current = (int) (offset.y + prevCount * (view.getMeasuredHeight() + pageGapPixels * scale) - view.getTop());
            }

            float size = (float) getHeight() * getHeight() / total;
            if (size < SCROLLBAR_MIN_THUMB_SIZE) {
                size = SCROLLBAR_MIN_THUMB_SIZE;
                total = (int) ((total - getHeight()) + total * size / getHeight());
            }
            float position = (float) getHeight() * current / total;
            drawVerticalScrollBar(canvas, position, size);
        } else if (!scrollMode || count == 1) {
            if (view.getMeasuredHeight() > getHeight()) {
                float position = (float) getHeight() * -view.getTop() / view.getMeasuredHeight();
                float size = (float) getHeight() * getHeight() / view.getMeasuredHeight();
                drawVerticalScrollBar(canvas, position, size);
            }
            if (view.getMeasuredWidth() > getWidth()) {
                float position = (float) getWidth() * -view.getLeft() / view.getMeasuredWidth();
                float size = (float) getWidth() * getWidth() / view.getMeasuredWidth();
                drawHorizontalScrollBar(canvas,  position, size);
            }
        }
    }
    
    private void drawHorizontalScrollBar(Canvas canvas, float position, float size) {
        if (position < 0) {
            size += position;
            position = 0;
        } else if (position + size > getWidth()) {
            size -= position + size - getWidth();
        }
        
        canvas.drawRoundRect(new RectF(
                position, getHeight() - SCROLLBAR_STROKE_WIDTH, position + size, getHeight()),
                4, 2, SCROLLBAR_PAINT);
    }
    
    private void drawVerticalScrollBar(Canvas canvas, float position, float size) {
        if (position < 0) {
            size += position;
            position = 0;
        } else if (position + size > getHeight()) {
            size -= position + size - getHeight();
        }
        
        canvas.drawRoundRect(new RectF(
                getWidth() - SCROLLBAR_STROKE_WIDTH, position, getWidth(), position + size),
                2, 4, SCROLLBAR_PAINT);
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        for (int i = 0; i < childViews.size(); i++) {
            measureView(childViews.valueAt(i));
        }
    }

    private void doLayout() {
        if (currentIndex == PageContentView.NO_INDEX) {
            return;
        }

        PageContentView cv = childViews.get(currentIndex);
        Point cvOffset;

        if (requestedScale != DEFAULT_SCALE) {
            scale = requestedScale;
            requestedScale = DEFAULT_SCALE;
        }

        if (!resetLayout) {
            // Move to next or previous if current is sufficiently off center
            if (cv != null && scrollMode && !sliding) {
                if (cv.getTop() + cv.getMeasuredHeight()
                    + pageGapPixels * scale / 2 + scrollOffsetY < 0) {
                    setCurrentIndexToRightOrDown();
                }
                if (cv.getTop() - pageGapPixels * scale / 2 + scrollOffsetY >= getHeight()) {
                    setCurrentIndexToLeftOrUp();
                }
            }

            // Remove not needed children and hold them for reuse
            for (int i = childViews.size() - 1; i >= 0; i--) {
                int index = childViews.keyAt(i);
                PageContentView v = childViews.get(index);
                if (scrollMode && (v.getTop() > getHeight() * 2 * scale || v.getBottom() < -getHeight() * scale)
                    || (!scrollMode && (index < currentIndex - 1 || index > currentIndex + 1))) {
                    v.clear();
                    viewCache.add(v);
                    removeViewInLayout(v);
                    childViews.remove(index);
                }
            }
        } else {
            resetLayout = false;
            scrollOffsetX = scrollOffsetY = 0;

            // Remove all children and hold them for reuse
            for (int i = 0; i < childViews.size(); i++) {
                PageContentView v = childViews.valueAt(i);
                v.clear();
                viewCache.add(v);
                removeViewInLayout(v);
            }
            childViews.clear();
        }

        // Ensure current view is present
        int cvLeft, cvRight, cvTop, cvBottom;
        boolean notPresent = (childViews.get(currentIndex) == null);
        cv = getOrCreateChild(currentIndex);
        // When the view is sub-screen-size in either dimension we
        // offset it to center within the screen area, and to keep
        // the views spaced out

        cvOffset = subScreenSizeOffset(cv);

        if (notPresent) {
            // Main item not already present. Just place it top left
            if (shouldKeepScrollOffset() && keptScrollOffset != null) {
                cvOffset = keptScrollOffset;
                keptScrollOffset = null;
            }
            cvLeft = cvOffset.x;
            cvTop  = cvOffset.y;
        } else {
            // Main item already present. Adjust by scroll offsets
            if (shouldKeepScrollOffset() && keptScrollOffset != null) {
                cvLeft = keptScrollOffset.x + scrollOffsetX;
                cvTop = keptScrollOffset.y + scrollOffsetY;
                keptScrollOffset = null;
            } else {
                cvLeft = cv.getLeft() + scrollOffsetX;
                cvTop  = cv.getTop()  + scrollOffsetY;
            }
        }

        // Scroll values have been accounted for
        scrollOffsetX = scrollOffsetY = 0;
        cvRight  = cvLeft + cv.getMeasuredWidth();
        cvBottom = cvTop  + cv.getMeasuredHeight();

        if (scrollMode) {
            if (!isLeftOrUpIndexAvailable() && cvTop > cvOffset.y) {
                cvTop = cvOffset.y;
                cvBottom = cvTop + cv.getMeasuredHeight();
                if (!scroller.isFinished()) {
                    tryOverFirst = !reverseMode;
                    tryOverLast = reverseMode;
                    scroller.forceFinished(true);
                }
            }
            if (!isRightOrDownIndexAvailable() && cvTop < cvOffset.y) {
                cvTop = cvOffset.y;
                cvBottom = cvTop + cv.getMeasuredHeight();
                if (!scroller.isFinished()) {
                    tryOverFirst = reverseMode;
                    tryOverLast = !reverseMode;
                    scroller.forceFinished(true);
                }
            }
            if (cvLeft > cvOffset.x) {
                cvLeft = cvOffset.x;
                cvRight = cvLeft + cv.getMeasuredWidth();
            }
            if (cvRight < getWidth() - cvOffset.x) {
                cvRight = getWidth() - cvOffset.x;
                cvLeft = cvRight - cv.getMeasuredWidth();
            }
        } else {
            if (cvBottom < getHeight() - cvOffset.y) {
                cvBottom = getHeight() - cvOffset.y;
                cvTop = cvBottom - cv.getMeasuredHeight();
            }

            if (cvTop > cvOffset.y) {
                cvTop = cvOffset.y;
                cvBottom = cvTop + cv.getMeasuredHeight();
            }

            if (!slidingEnabled) {
                if (cvRight < getWidth() - cvOffset.x) {
                    cvRight = getWidth() - cvOffset.x;
                    cvLeft = cvRight - cv.getMeasuredWidth();
                }

                if (cvLeft > cvOffset.x) {
                    cvLeft = cvOffset.x;
                    cvRight = cvLeft + cv.getMeasuredWidth();
                }
            }
        }

        if (!userInteracting && scroller.isFinished()) {
            Point corr = getCorrection(getScrollBounds(cvLeft, cvTop, cvRight, cvBottom));
            cvRight  += corr.x;
            cvLeft   += corr.x;
            cvTop    += corr.y;
            cvBottom += corr.y;
        } else if (!scaling && cv.getMeasuredHeight() <= getHeight() && !scrollMode) {
            // When the current view is as small as the screen in height, clamp
            // it vertically
            Point corr = getCorrection(getScrollBounds(cvLeft, cvTop, cvRight, cvBottom));
            cvTop    += corr.y;
            cvBottom += corr.y;
        } else if (!scaling && cv.getMeasuredWidth() <= getWidth() && scrollMode) {
            // When the current view is as small as the screen in width, clamp
            // it horizontally
            Point corr = getCorrection(getScrollBounds(cvLeft, cvTop, cvRight, cvBottom));
            cvLeft   += corr.x;
            cvRight  += corr.x;
        }

        cv.layout(cvLeft, cvTop, cvRight, cvBottom);

        if (scrollMode) {
            int margin = (int) (pageGapPixels * scale);
            while (isLeftOrUpIndexAvailable(cv.getIndex())) {
                int lvLeft, lvTop, lvRight, lvBottom;
                lvBottom = cv.getTop() - margin;
                if (lvBottom < -getHeight() * scale) {
                    break;
                }
                int index = reverseMode ? cv.getIndex() + 1 : cv.getIndex() - 1;
                PageContentView lv = getOrCreateChild(index);
                lvTop = lvBottom - lv.getMeasuredHeight();
                lvLeft = (cv.getRight() + cv.getLeft() - lv.getMeasuredWidth()) / 2;
                lvRight = (cv.getRight() + cv.getLeft() + lv.getMeasuredWidth()) / 2;
                lv.layout(lvLeft, lvTop, lvRight, lvBottom);
                cv = lv;
            }
            for (int i = childViews.size() - 1; i >= 0; i--) {
                int index = childViews.keyAt(i);
                if ((reverseMode && index > cv.getIndex()) || (!reverseMode && index < cv.getIndex())) {
                    PageContentView v = childViews.get(index);
                    v.clear();
                    childViews.remove(index);
                    viewCache.add(v);
                    removeViewInLayout(v);
                }
            }
            if (!isLeftOrUpIndexAvailable(cv.getIndex()) && cv.getTop() >= 0) {
                int prevCurrentIndex = currentIndex;
                currentIndex = cv.getIndex();
                cv = childViews.get(prevCurrentIndex);
            } else {
                cv = childViews.get(currentIndex);
            }
            while (isRightOrDownIndexAvailable(cv.getIndex())) {
                int rvLeft, rvTop, rvRight, rvBottom;
                rvTop = cv.getBottom() + margin;
                if (rvTop > getHeight() * 2 * scale) {
                    break;
                }
                int index = reverseMode ? cv.getIndex() - 1 : cv.getIndex() + 1;
                PageContentView rv = getOrCreateChild(index);
                rvBottom = rvTop + rv.getMeasuredHeight();
                rvLeft = (cv.getRight() + cv.getLeft() - rv.getMeasuredWidth()) / 2;
                rvRight = (cv.getRight() + cv.getLeft() + rv.getMeasuredWidth()) / 2;
                rv.layout(rvLeft, rvTop, rvRight, rvBottom);
                cv = rv;
            }
            for (int i = childViews.size() - 1; i >= 0; i--) {
                int index = childViews.keyAt(i);
                if ((reverseMode && index < cv.getIndex()) || (!reverseMode && index > cv.getIndex())) {
                    PageContentView v = childViews.get(index);
                    v.clear();
                    childViews.remove(index);
                    viewCache.add(v);
                    removeViewInLayout(v);
                }
            }
            if (adapter.getCount() > 1
                && !isRightOrDownIndexAvailable(cv.getIndex()) && cv.getBottom() <= getHeight()) {
                currentIndex = cv.getIndex();
            }
        } else {
            if (isLeftOrUpIndexAvailable()) {
                PageContentView lv = getOrCreateChild(reverseMode ? currentIndex + 1 : currentIndex - 1);
                Point leftOffset = subScreenSizeOffset(lv);
                int lvLeft, lvTop, lvRight, lvBottom;
                lvTop = (cvBottom + cvTop - lv.getMeasuredHeight()) / 2;
                lvBottom = (cvBottom + cvTop + lv.getMeasuredHeight()) / 2;
                if (lvTop < 0) {
                    lvBottom += -lvTop;
                    lvTop = 0;
                }
                int margin = leftOffset.x + pageGapPixels + cvOffset.x;
                lvRight = cvLeft - margin;
                if (scaling && lvRight > 0) {
                    lvRight = -pageGapPixels - leftOffset.x;
                }
                lvLeft = lvRight - lv.getMeasuredWidth();
                lv.layout(lvLeft, lvTop, lvRight, lvBottom);
            }
            if (isRightOrDownIndexAvailable()) {
                PageContentView rv = getOrCreateChild(reverseMode ? currentIndex - 1 : currentIndex + 1);
                Point rightOffset = subScreenSizeOffset(rv);
                int rvLeft, rvTop, rvRight, rvBottom;
                rvTop = (cvBottom + cvTop - rv.getMeasuredHeight()) / 2;
                rvBottom = (cvBottom + cvTop + rv.getMeasuredHeight()) / 2;
                if (rvTop < 0) {
                    rvBottom += -rvTop;
                    rvTop = 0;
                }
                int margin = cvOffset.x + pageGapPixels + rightOffset.x;
                rvLeft = cvRight + margin;
                if (scaling && rvLeft < getWidth()) {
                    rvLeft = getWidth() + pageGapPixels + rightOffset.x;
                }
                rvRight = rvLeft + rv.getMeasuredWidth();
                rv.layout(rvLeft, rvTop, rvRight, rvBottom);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        doLayout();

        invalidate();
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isExternalGestureMode) {
            scaleGestureDetector.onTouchEvent(event);
        }

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            PageContentView view = childViews.get(currentIndex);
            if (view != null) {
                touchStartOffset = new Pair<>(view.getLeft(), view.getRight());
            }
        }
        if (gestureDetector.onTouchEvent(event)) {
            touchStartOffset = null;
            return true;
        }
        
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            userInteracting = true;
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            isExternalGestureMode = false;
            scrollDisabled = false;
            
            if (scrollMode) {
                if (scroller.isFinished()) {
                    settleOrUnsettleViews();
                }
            } else {
                userInteracting = false;

                PageContentView view = childViews.get(currentIndex);
                if (view != null && !scaling) {
                    Point cvOffset = subScreenSizeOffset(view);

                    int right = view.getLeft() + view.getMeasuredWidth()
                            + cvOffset.x + pageGapPixels / 2 + scrollOffsetX;
                    if (isViewingNextPageByGestureAvailable() && right < getWidth()) {
                        if (!isRightOrDownIndexAvailable()) {
                            tryOverFirst = reverseMode;
                            tryOverLast = !reverseMode;
                        } else if (right < getWidth() / 2) {
                            setCurrentIndexToRightOrDown();
                        }
                    }

                    int left = view.getLeft() - cvOffset.x - pageGapPixels / 2 + scrollOffsetX;
                    if (isViewingPrevPageByGestureAvailable() && left > 0) {
                        if (!isLeftOrUpIndexAvailable()) {
                            tryOverFirst = !reverseMode;
                            tryOverLast = reverseMode;
                        } else if (left >= getWidth() / 2) {
                            setCurrentIndexToLeftOrUp();
                        }
                    }
                }
                
                view = childViews.get(currentIndex);
                
                if (view != null) {
                    if (scroller.isFinished() && slidingEnabled) {
                        // If, at the end of user interaction, there is no
                        // current inertial scroll in operation then animate
                        // the view onto screen if necessary
                        slideViewOntoScreen(view);
                    }
                    
                    if (scroller.isFinished()) {
                        // If still there is no inertial scroll in operation
                        // then the layout is stable
                        postSettle(view);
                    }
                }
            }
            touchStartOffset = null;
            listener.onTouchUp();
        }
        return true;
    }
    
    private void settleOrUnsettleViews() {
        List<PageContentView> toBeSettled = new ArrayList<>();
        for (int i = 0; i < childViews.size(); i++) {
            PageContentView view = childViews.valueAt(i);
            Rect rect = new Rect(view.getLeft(), view.getTop(),
                                 view.getRight(), view.getBottom());
            
            if (rect.intersect(0, 0, getWidth(), getHeight())) {
                toBeSettled.add(view);
            } else {
                postUnsettle(view);
            }
        }
        
        for (PageContentView view : toBeSettled) {
            postSettle(view);
        }
    }

    @Override
    public PageContentViewAdapter getAdapter() {
        return adapter;
    }

    @Override
    public void setAdapter(PageContentViewAdapter adapter) {
        if (this.adapter != null) {
            this.adapter.unregisterDataSetObserver(dataSetObserver);
        }
        this.adapter = adapter;
        refresh();
        adapter.registerDataSetObserver(dataSetObserver);
    }
    
    private void refresh() {
        for (int i = 0; i < childViews.size(); i++) {
            PageContentView v = childViews.valueAt(i);
            v.clear();
            removeViewInLayout(v);
        }
        childViews.clear();
        viewCache.clear();
        removeAllViewsInLayout();
        scale = DEFAULT_SCALE;
        resetLayout = true;
        requestLayout();
    }
    
    @Override
    public View getSelectedView() {
        return null;
    }

    @Override
    public void setSelection(int position) {
    }
    
    private int directionOfTravel(float deltaX, float deltaY, float velocityX, float velocityY) {
        if (Math.abs(deltaX) > flingDistanceThreshold
                && Math.abs(velocityX) > flingVelocityThreshold
                && Math.abs(velocityX) >  1.5f * Math.abs(velocityY)) {
            return (deltaX > 0) ? MOVING_RIGHT : MOVING_LEFT;
        } else if (Math.abs(deltaY) > flingDistanceThreshold
                && Math.abs(velocityY) > flingVelocityThreshold
                && Math.abs(velocityY) >  1.5f * Math.abs(velocityX)) {
            return (deltaY > 0) ? MOVING_DOWN : MOVING_UP;
        } else {
            return MOVING_DIAGONALLY;
        }
    }

    private boolean withinBoundsInDirectionOfTravel(
            Rect bounds, float deltaX, float deltaY, float velocityX, float velocityY) {
        switch (directionOfTravel(deltaX, deltaY, velocityX, velocityY)) {
            case MOVING_DIAGONALLY: return bounds.contains(0, 0);
            case MOVING_LEFT:       return bounds.left <= 0;
            case MOVING_RIGHT:      return bounds.right >= 0;
            case MOVING_UP:         return bounds.top <= 0;
            case MOVING_DOWN:       return bounds.bottom >= 0;
            default: throw new IllegalStateException();
        }
    }
    
    private void slideViewOntoScreen(View view) {
        Point corr = getCorrection(getScrollBounds(view));
        if (corr.x != 0 || corr.y != 0) {
            scrollerLastX = scrollerLastY = 0;
            sliding = true;
            scroller.startScroll(0, 0, corr.x, corr.y, 400);
        }
        post(scrollProcessor);
    }

    @Override
    public boolean onDown(MotionEvent e) {
        if (!sliding) {
            scroller.forceFinished(true);
        }
        return false;
    }
    
    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return !doubleTapScalingEnabled && listener.onSingleTapUp(e);
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (!scrollDisabled) {
            if (scale == DEFAULT_SCALE && !scrollMode && !scaleGestureDetector.isInProgress()) {
                boolean result = listener.onScrollWithoutScaling(e1, e2, distanceX, distanceY);
                isExternalGestureMode |= result;
                if (result) {
                    return true;
                }
            }

            if (Math.abs(distanceX) > 1.5f * Math.abs(distanceY)) {
                scrollOffsetX -= distanceX;
            } else if (Math.abs(distanceY) > 1.5f * Math.abs(distanceX)) {
                scrollOffsetY -= distanceY;
            } else {
                scrollOffsetX -= distanceX;
                scrollOffsetY -= distanceY;
            }
            requestLayout();
        }
        return false;
    }

    public void scrollVerticalInScrollMode(int offset) {
        if (!scrollMode) {
            return;
        }
        
        scrollOffsetY -= offset;
        requestLayout();

        PageContentView cv = childViews.get(currentIndex);

        if (cv == null) {
            return;
        }

        Point cvOffset = subScreenSizeOffset(cv);
        int cvTop = cv.getTop() + scrollOffsetY;

        if (!isRightOrDownIndexAvailable() && cvTop < cvOffset.y) {
            listener.onTryOverLastPage();
        }
    }


    @Override
    public void onLongPress(MotionEvent e) {
    }

    private boolean isViewingPrevPageByGestureAvailable() {
        return scale == DEFAULT_SCALE
                || (touchStartOffset != null && touchStartOffset.first == 0);
    }

    private boolean isViewingNextPageByGestureAvailable() {
        return scale == DEFAULT_SCALE
                || (touchStartOffset != null && touchStartOffset.second == getWidth());
    }
    
    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
            float velocityY) {
        if (scrollDisabled || e1 == null || e2 == null) {
            return false;
        }
        
        View view = childViews.get(currentIndex);
        if (view == null) {
            return false;
        }
        
        Rect bounds = getScrollBounds(view);
        
        if (scrollMode) {
            scrollerLastX = scrollerLastY = 0;

            int minX = bounds.left;
            int maxX = bounds.right;
            int maxY = (int) (Math.abs(velocityY));
            int minY = -maxY;
            
            scroller.fling(0, 0, (int) velocityX, (int) velocityY, minX, maxX, minY, maxY);
            post(scrollProcessor);
            return true;
        }

        float deltaX = e2.getX() - e1.getX();
        float deltaY = e2.getY() - e1.getY();
        int direction = directionOfTravel(deltaX, deltaY, velocityX, velocityY);
        switch (direction) {
            case MOVING_LEFT:
                if (bounds.left >= 0) {
                    if (!shouldKeepScrollOffset() && isViewingNextPageByGestureAvailable()) {
                        viewRightOrDown();
                        return true;
                    }
                }
                break;
            case MOVING_RIGHT:
                if (bounds.right <= 0) {
                    if (!shouldKeepScrollOffset() && isViewingPrevPageByGestureAvailable()) {
                        viewLeftOrUp();
                        return true;
                    }
                }
                break;
        }

        scroller.forceFinished(true);
        scrollerLastX = scrollerLastY = 0;
        // If the page has been dragged out of bounds then we want to spring back
        // nicely. fling jumps back into bounds instantly, so we don't want to use
        // fling in that case. On the other hand, we don't want to forgo a fling
        // just because of a slightly off-angle drag taking us out of bounds other
        // than in the direction of the drag, so we test for out of bounds only
        // in the direction of travel.
        //
        // Also don't fling if out of bounds in any direction by more than fling
        // margin
        Rect expandedBounds = new Rect(bounds);
        expandedBounds.inset(-FLING_MARGIN, -FLING_MARGIN);
        
        if(withinBoundsInDirectionOfTravel(bounds, deltaX, deltaY, velocityX, velocityY)
                && expandedBounds.contains(0, 0)) {
            scroller.fling(0, 0, (int) velocityX, (int) velocityY, bounds.left, bounds.right, bounds.top, bounds.bottom);
            post(scrollProcessor);
        }
        
        return false;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        if (!canScaling()) {
            return false;
        }
        prepareScaling();
        return true;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        doScaling(scale * detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        endScaling();
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (!canScaling()) {
            onSingleTapConfirmed(e);
            return true;
        }

        View view = childViews.get(currentIndex);
        if (view == null) {
            onSingleTapConfirmed(e);
            return true;
        }

        /*
         * Double tap 확대 / 축소
         *
         * 1. DEFAULT_SCALE이 아닐 경우에는, DEFAULT_SCALE 맞춰준다.
         *
         * 2. DEFAULT_SCALE인 경우에는 아래의 방법에 따라 확대한다.
         *  - content width > content height : 두 쪽 보기 - (content width / 2 (한 쪽 content width)가 화면에 가득차도록 확대)
         *
         *  - content width < content height : 한 쪽 보기 - 1.5배 확대
         */
        float focusX = e.getX();
        float focusY = e.getY();
        float contentWidth = view.getWidth();
        float contentHeight = view.getHeight();
        float toScale;
        if (scale == DEFAULT_SCALE) {
            if (focusX < view.getLeft() || focusX > view.getLeft() + contentWidth
                    || focusY < view.getTop() || focusY > view.getTop() + contentHeight) {
                onSingleTapConfirmed(e);
                return true;
            }

            if (contentWidth > contentHeight) {
                contentWidth /= 2.0f;
                toScale = getWidth() / contentWidth;

                if (focusX < getWidth() / 2.0f) {
                    focusX = getLeft();
                } else {
                    focusX = getRight();
                }
            } else {
                toScale = 1.5f;
            }
        } else {
            toScale = DEFAULT_SCALE;

            if (contentWidth > contentHeight) {
                if (focusX - view.getLeft() < contentWidth / 2.0f) {
                    focusX = getLeft();
                } else {
                    focusX = getRight();
                }
            }
        }

        prepareScaling();
        scaleWithAnimation(toScale, focusX, focusY);

        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        return listener.onSingleTapUp(e);
    }

    private boolean canScaling() {
        PageContentView view = childViews.get(currentIndex);
        return (view != null && view.isRendered());
    }

    private void prepareScaling() {
        scaling = true;
        scrollOffsetX = scrollOffsetY = 0;
        scrollDisabled = true;
    }

    private void scaleWithAnimation(float toScale, final float focusX, final float focusY) {
        int animationCount = 100;
        int animationDurationMillis = 200;

        AccelerateDecelerateInterpolator interpolator = new AccelerateDecelerateInterpolator();
        for (int i = 0; i < animationCount; i++) {
            final float scaleBit;
            if (toScale == DEFAULT_SCALE) {
                // 축소
                scaleBit = scale - ((scale - DEFAULT_SCALE) * (i + 1) / animationCount);
            } else {
                // 확대
                scaleBit = (toScale / animationCount) * (i + 1);
            }

            final boolean isLastExecution = (i == animationCount - 1);
            int delayMillis = (int) (interpolator.getInterpolation((float) i / animationCount) * animationDurationMillis);
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    doScaling(scaleBit, focusX, focusY);
                    if (isLastExecution) {
                        scrollDisabled = false;
                        endScaling();
                        PageContentView view = childViews.get(currentIndex);
                        if (view != null) {
                            postSettle(view);
                        }
                    }
                }
            }, delayMillis);
        }
    }

    private void doScaling(float toScale, float focusX, float focusY) {
        float prevScale = scale;
        scale = Math.min(Math.max(toScale, MIN_SCALE), MAX_SCALE);
        float factor = scale / prevScale;

        View view = childViews.get(currentIndex);
        if (view == null) {
            return;
        }

        int viewFocusX = (int) focusX - (view.getLeft() + scrollOffsetX);
        int viewFocusY = (int) focusY - (view.getTop() + scrollOffsetY);

        scrollOffsetX += viewFocusX - viewFocusX * factor;
        scrollOffsetY += viewFocusY - viewFocusY * factor;

        requestLayout();
    }

    private void endScaling() {
        if (scrollMode) {
            scaling = false;
        }
    }

    private void viewLeftOrUp() {
        if (reverseMode) {
            viewNext();
        } else {
            viewPrev();
        }
    }
    
    public void viewPrev() {
        // Fling off to the right/down bring previous view onto screen
        userInteracting = false;
        
        if (currentIndex > 0) {
            keepScrollOffsetIfNeeded();
            decreaseCurrentIndex();
        } else {
            tryOverFirst = true;
        }
        
        if (slidingEnabled) {
            View view = childViews.get(currentIndex);
            if (view != null) {
                slideViewOntoScreen(view);
            }
        } else {
            doLayout();
            post(scrollProcessor);
        }
    }

    private void viewRightOrDown() {
        if (reverseMode) {
            viewPrev();
        } else {
            viewNext();
        }
    }
    
    public void viewNext() {
        // Fling off to the right/down bring previous view onto screen
        userInteracting = false;
        
        if (currentIndex + 1 < adapter.getCount()) {
            keepScrollOffsetIfNeeded();
            increaseCurrentIndex();
        } else {
            tryOverLast = true;
        }
        
        if (slidingEnabled) {
            View view = childViews.get(currentIndex);
            if (view != null) {
                slideViewOntoScreen(view);
            }
        } else {
            doLayout();
            post(scrollProcessor);
        }
    }
    
    public void destroy() {
        for (int i = 0; i < childViews.size(); i++) {
            PageContentView view = childViews.valueAt(i);
            view.clear();
        }
        
        childViews.clear();
        viewCache.clear();
    }

    private void keepScrollOffsetIfNeeded() {
        if (shouldKeepScrollOffset()) {
            View view = childViews.get(currentIndex);
            if (view != null) {
                keptScrollOffset = new Point(view.getLeft(), view.getTop());
            }
        }
    }

    public void requestScale(float scale, Point scrollOffset) {
        requestedScale = scale;
        if (keepScrollOffsetEnabled && scrollOffset != null) {
            keptScrollOffset = scrollOffset;
        }
    }

    public float getScale() {
        return scale;
    }

    public Point getScrollOffset() {
        View view = childViews.get(currentIndex);
        if (view == null) {
            return null;
        }

        return new Point(view.getLeft(), view.getTop());
    }

    public List<Integer> getVisibleChildIndexList() {
        List<Integer> indexList = new ArrayList<>();
        for (int i = 0; i < childViews.size(); i++) {
            indexList.add(childViews.keyAt(i));
        }
        return indexList;
    }

    public PageContentView.Size getRenderSize() {
        PageContentView currentView = childViews.get(currentIndex);
        if (currentView != null) {
            return childViews.get(currentIndex).getRenderSize();
        } else if (childViews.size() != 0) {
            // TODO : 원인 확인 필요
            // 알 수 없는 이유로 current index가 비어있을경우 사이즈가 같을 가능성이 높은 첫번재 뷰의 값을 리턴함.
            return childViews.get(childViews.keyAt(0)).getRenderSize();
        } else {
            // childViews가 완전 비어있을경우 size(1,1)을 리턴함.
            return new PageContentView.Size(1, 1);
        }
    }

    public boolean isCurrentViewExist() {
        return childViews.get(currentIndex) != null;
    }
}
