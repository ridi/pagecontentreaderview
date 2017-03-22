package com.ridi.books.viewer.reader.pagecontent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
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
import java.util.NoSuchElementException;

public class PageContentReaderView extends AdapterView<PageContentViewAdapter>
                           implements GestureDetector.OnGestureListener,
                                      GestureDetector.OnDoubleTapListener,
                                      ScaleGestureDetector.OnScaleGestureListener {
    private static final float MIN_SCALE                        = 1.0f;
    private static final float MAX_SCALE                        = 5.0f;

    private static final int DEFAULT_FLING_DISTANCE_THRESHOLD   = 120;
    private static final int DEFAULT_FLING_VELOCITY_THRESHOLD   = 1000;
    private static final int FLING_MARGIN                       = 70;

    private static final int SCROLLBAR_MIN_THUMB_SIZE           = 5;
    private static final int SCROLLBAR_STROKE_WIDTH             = 8;
    private static final int SCROLLBAR_COLOR                    = 0x88808080;

    private static final int GAP_DP                             = 5;
    
    private static final int MOVING_DIAGONALLY                  = 0;
    private static final int MOVING_UP                          = 1;
    private static final int MOVING_RIGHT                       = 2;
    private static final int MOVING_DOWN                        = 3;
    private static final int MOVING_LEFT                        = 4;
    
    public interface Listener {
        void onViewModeChanged();
        boolean onSingleTapUp(MotionEvent e);
        boolean onScrollWithoutScaling(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY);
        void onScrollChanged();
        void onCurrentIndexChanged();
        void onTryOverFirstPage();
        void onTryOverLastPage();
    }

    private PageContentViewAdapter adapter;
    private int currentIndex;  // Adapter's index for the current view
    private boolean resetLayout;
    private SparseArray<PageContentView> childViews;
    private LinkedList<PageContentView> viewCache;

    private boolean scrollMode;
    private boolean reverseMode;
    private boolean slidingEnabled;

    private boolean keepScrollOffset;
    private Point keptScrollOffset;
    
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    private float scale;
    private boolean scaling;
    private boolean doubleTapScalingEnabled;
    private float initialScale = MIN_SCALE;
    private int flingDistanceThreshold = DEFAULT_FLING_DISTANCE_THRESHOLD;
    private int flingVelocityThreshold = DEFAULT_FLING_VELOCITY_THRESHOLD;
    
    private Scroller scroller;
    private int scrollOffsetX;
    private int scrollOffsetY;
    private int scrollerLastX;
    private int scrollerLastY;
    private boolean scrollDisabled;
    
    private boolean userInteracting;
    private boolean sliding;
    
    private Listener listener;
    
    private boolean tryOverFirst;
    private boolean tryOverLast;
    
    public PageContentReaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        gestureDetector = new GestureDetector(context, this);
        scale = initialScale;
        scaleGestureDetector = new ScaleGestureDetector(context, this);
        scroller = new Scroller(context);
        
        childViews = new SparseArray<>(3);
        viewCache = new LinkedList<>();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        clearViewCache();
        notifyAdapterChanged();
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

    public void setKeepScrollOffset(boolean keepScrollOffset) {
        this.keepScrollOffset = keepScrollOffset;
    }

    public void setDoubleTapScalingEnabled(boolean doubleTapScalingEnabled) {
        this.doubleTapScalingEnabled = doubleTapScalingEnabled;
        gestureDetector.setOnDoubleTapListener(doubleTapScalingEnabled ? this : null);
    }

    public void setCurrentIndex(int index) {
        if (index >= 0 && index < adapter.getCount()) {
            currentIndex = index;
            listener.onCurrentIndexChanged();
            scale = initialScale;
            resetLayout = true;
            requestLayout();
        }
    }
    
    public int getCurrentIndex() {
        return currentIndex;
    }

    private boolean isLeftOrUpIndexAvailable() {
        return (reverseMode && currentIndex + 1 < adapter.getCount()) || (!reverseMode && currentIndex > 0);
    }

    private boolean isRightOrDownIndexAvailable() {
        return (reverseMode && currentIndex > 0) || (!reverseMode && currentIndex + 1 < adapter.getCount());
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
    
    private Point subScreenSizeOffset(View view) {
        return new Point(Math.max((getWidth() - view.getMeasuredWidth()) / 2, 0),
                         Math.max((getHeight() - view.getMeasuredHeight()) / 2, 0));
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
        if (xMin > xMax) xMin = xMax = (xMin + xMax) / 2;
        if (yMin > yMax) yMin = yMax = (yMin + yMax) / 2;

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
                    
                    for (int i = 0 ; i < childViews.size() ; i++) {
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
        
        if (adapter.getCount() > 0) {
           drawScrollBars(canvas);
        }
    }
    
    private void drawScrollBars(Canvas canvas) {
        View view = childViews.get(currentIndex);
        if (view == null || view.getMeasuredWidth() == 0 || view.getMeasuredHeight() == 0) {
            return;
        }
        
        Point offset = subScreenSizeOffset(view);
        int count = adapter.getCount();
        
        Paint paint = new Paint();
        paint.setColor(SCROLLBAR_COLOR);
        paint.setStrokeWidth(SCROLLBAR_STROKE_WIDTH);
        
        if (scrollMode && count > 1) {
            int prevCount = reverseMode ? count - currentIndex - 1 : currentIndex;
            int gap = dipToPixel(GAP_DP);
            
            if (isScrollMode()) {
                int total = (int) (offset.y * 2 + view.getMeasuredHeight() * count + gap * scale * (count - 1));
                int current = (int) (offset.y + prevCount * (view.getMeasuredHeight() + gap * scale) - view.getTop());

                float size = (float) getHeight() * getHeight() / total;
                if (size < SCROLLBAR_MIN_THUMB_SIZE) {
                    size = SCROLLBAR_MIN_THUMB_SIZE;
                    total = (int) ((total - getHeight()) + total * size / getHeight());
                }
                float position = (float) getHeight() * current / total;

                drawVerticalScrollBar(canvas, paint, position, size);
            } else {
                int total = (int) (offset.x * 2 + view.getMeasuredWidth() * count + gap * scale * (count - 1));
                int current = (int) (offset.x + prevCount * (view.getMeasuredWidth() + gap * scale) - view.getLeft());

                float size = (float) getWidth() * getWidth() / total;
                if (size < SCROLLBAR_MIN_THUMB_SIZE) {
                    size = SCROLLBAR_MIN_THUMB_SIZE;
                    total = (int) ((total - getWidth()) + total * size / getWidth());
                }
                float position = (float) getWidth() * current / total;

                drawHorizontalScrollBar(canvas, paint, position, size);
            }
        }
        
        if (!scrollMode || count == 1) {
            if (view.getMeasuredHeight() > getHeight()) {
                float position = (float) getHeight() * -view.getTop() / view.getMeasuredHeight();
                float size = (float) getHeight() * getHeight() / view.getMeasuredHeight();
                
                drawVerticalScrollBar(canvas, paint, position, size);
            }
            if (view.getMeasuredWidth() > getWidth()) {
                float position = (float) getWidth() * -view.getLeft() / view.getMeasuredWidth();
                float size = (float) getWidth() * getWidth() / view.getMeasuredWidth();

                drawHorizontalScrollBar(canvas, paint, position, size);
            }
        }
    }
    
    private void drawHorizontalScrollBar(Canvas canvas, Paint paint, float position, float size) {
        if (position < 0) {
            size += position;
            position = 0;
        }
        else if (position + size > getWidth()) {
            size -= position + size - getWidth();
        }
        
        canvas.drawRoundRect(new RectF(position, getHeight() - SCROLLBAR_STROKE_WIDTH, position + size, getHeight()), 4, 2, paint);
    }
    
    private void drawVerticalScrollBar(Canvas canvas, Paint paint, float position, float size) {
        if (position < 0) {
            size += position;
            position = 0;
        }                           
        else if (position + size > getHeight()) {
            size -= position + size - getHeight();
        }
        
        canvas.drawRoundRect(new RectF(getWidth() - SCROLLBAR_STROKE_WIDTH, position, getWidth(), position + size), 2, 4, paint);
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        
        for (int i = 0 ; i < childViews.size() ; i++) {
            measureView(childViews.valueAt(i));
        }
    }
    
    @Override
    protected void onLayout(boolean changed, int left, int top, int right,
            int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        
        PageContentView cv = childViews.get(currentIndex);
        Point cvOffset;
        
        if (!resetLayout) {
            // Move to next or previous if current is sufficiently off center
            if (cv != null && scrollMode && !sliding) {
                int gap = dipToPixel(GAP_DP);
                cvOffset = subScreenSizeOffset(cv);
                if (cv.getTop() + cv.getMeasuredHeight() + cvOffset.y + gap * scale / 2 + scrollOffsetY < getHeight() / 2) {
                    setCurrentIndexToRightOrDown();
                }
                if (cv.getTop() - cvOffset.y - gap * scale / 2 + scrollOffsetY >= getHeight() / 2) {
                    setCurrentIndexToLeftOrUp();
                }
            }
            
            // Remove not needed children and hold them for reuse
            int numChildren = childViews.size();
            int childIndices[] = new int[numChildren];
            for (int i = 0; i < numChildren; i++) {
                childIndices[i] = childViews.keyAt(i);
            }

            for (int i = 0; i < numChildren; i++) {
                int ai = childIndices[i];
                if (ai < currentIndex - 1 || ai > currentIndex + 1) {
                    PageContentView v = childViews.get(ai);
                    v.clear();
                    viewCache.add(v);
                    removeViewInLayout(v);
                    childViews.remove(ai);
                }
            }
        } else {
            resetLayout = false;
            scrollOffsetX = scrollOffsetY = 0;

            // Remove all children and hold them for reuse
            int numChildren = childViews.size();
            for (int i = 0; i < numChildren; i++) {
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
            if (keepScrollOffset && keptScrollOffset != null) {
                cvOffset = keptScrollOffset;
                keptScrollOffset = null;
            }
            cvLeft = cvOffset.x;
            cvTop  = cvOffset.y;
        } else {
            // Main item already present. Adjust by scroll offsets
            if (keepScrollOffset && keptScrollOffset != null) {
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
            if (!isLeftOrUpIndexAvailable()) {
                if (cvTop > cvOffset.y) {
                    cvTop = cvOffset.y;
                    cvBottom = cvTop + cv.getMeasuredHeight();
                    if (!scroller.isFinished()) {
                        tryOverFirst = !reverseMode;
                        tryOverLast = reverseMode;
                        scroller.forceFinished(true);
                    }
                }
            }
            if (!isRightOrDownIndexAvailable()) {
                if (cvBottom < getHeight() - cvOffset.y) {
                    cvBottom = getHeight() - cvOffset.y;
                    cvTop = cvBottom - cv.getMeasuredHeight();
                    if (!scroller.isFinished()) {
                        tryOverFirst = reverseMode;
                        tryOverLast = !reverseMode;
                        scroller.forceFinished(true);
                    }
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
            // When the current view is as small as the screen in widthi, clamp
            // it horizontally
            Point corr = getCorrection(getScrollBounds(cvLeft, cvTop, cvRight, cvBottom));
            cvLeft   += corr.x;
            cvRight  += corr.x;
        }
        
        cv.layout(cvLeft, cvTop, cvRight, cvBottom);

        int gap = dipToPixel(GAP_DP);

        if (isLeftOrUpIndexAvailable()) {
            View lv = getOrCreateChild(reverseMode ? currentIndex + 1 : currentIndex - 1);
            Point leftOffset = subScreenSizeOffset(lv);
            
            int margin = (int) (gap * scale);
            int lvLeft, lvTop, lvRight, lvBottom;
            
            if (scrollMode) {
                lvLeft = (cvRight + cvLeft - lv.getMeasuredWidth()) / 2;
                lvRight = (cvRight + cvLeft + lv.getMeasuredWidth()) / 2;

                if (lvLeft < 0 && !scrollMode) {
                    lvRight += -lvLeft;
                    lvLeft = 0;
                }

                if (!scrollMode) {
                    margin = leftOffset.y + gap + cvOffset.y;
                }
                lvBottom = cvTop - margin;
                if (!scrollMode && scaling) {
                    if (lvBottom > 0) {
                        lvBottom = -gap - leftOffset.y;
                    }
                }
                lvTop = lvBottom - lv.getMeasuredHeight();
            } else {
                lvTop = (cvBottom + cvTop - lv.getMeasuredHeight()) / 2;
                lvBottom = (cvBottom + cvTop + lv.getMeasuredHeight()) / 2;

                if (lvTop < 0 && !scrollMode) {
                    lvBottom += -lvTop;
                    lvTop = 0;
                }

                if (!scrollMode) {
                    margin = leftOffset.x + gap + cvOffset.x;
                }
                lvRight = cvLeft - margin;
                if (!scrollMode && scaling) {
                    if (lvRight > 0) {
                        lvRight = -gap - leftOffset.x;
                    }
                }
                lvLeft = lvRight - lv.getMeasuredWidth();
            }
            
            lv.layout(lvLeft, lvTop, lvRight, lvBottom);
        }

        if (isRightOrDownIndexAvailable()) {
            View rv = getOrCreateChild(reverseMode ? currentIndex - 1 : currentIndex + 1);
            Point rightOffset = subScreenSizeOffset(rv);
            
            int margin = (int) (gap * scale);
            int rvLeft, rvTop, rvRight, rvBottom;
            
            if (scrollMode) {
                rvLeft = (cvRight + cvLeft - rv.getMeasuredWidth()) / 2;
                rvRight = (cvRight + cvLeft + rv.getMeasuredWidth()) / 2;

                if (rvLeft < 0 && !scrollMode) {
                    rvRight += -rvLeft;
                    rvLeft = 0;
                }

                if (!scrollMode) {
                    margin = cvOffset.y + gap + rightOffset.y;
                }
                rvTop = cvBottom + margin;
                if (!scrollMode && scaling) {
                    if (rvTop < getHeight()) {
                        rvTop = getHeight() + gap + rightOffset.y;
                    }
                }
                rvBottom = rvTop + rv.getMeasuredHeight();
            } else {
                rvTop = (cvBottom + cvTop - rv.getMeasuredHeight()) / 2;
                rvBottom = (cvBottom + cvTop + rv.getMeasuredHeight()) / 2;

                if (rvTop < 0 && !scrollMode) {
                    rvBottom += -rvTop;
                    rvTop = 0;
                }

                if (!scrollMode) {
                    margin = cvOffset.x + gap + rightOffset.x;
                }
                rvLeft = cvRight + margin;
                if (!scrollMode && scaling) {
                    if (rvLeft < getWidth()) {
                        rvLeft = getWidth() + gap + rightOffset.x;
                    }
                }
                rvRight = rvLeft + rv.getMeasuredWidth();
            }
            
            rv.layout(rvLeft, rvTop, rvRight, rvBottom);
        }
        
        invalidate();
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        
        if (gestureDetector.onTouchEvent(event)) {
            return true;
        }
        
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            userInteracting = true;
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
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
                    int gap = dipToPixel(GAP_DP);

                    int right = view.getLeft() + view.getMeasuredWidth() + cvOffset.x + gap / 2 + scrollOffsetX;
                    if (right < getWidth()) {
                        if (!isRightOrDownIndexAvailable()) {
                            tryOverFirst = reverseMode;
                            tryOverLast = !reverseMode;
                        } else if (right < getWidth() / 2) {
                            setCurrentIndexToRightOrDown();
                        }
                    }

                    int left = view.getLeft() - cvOffset.x - gap / 2 + scrollOffsetX;
                    if (left > 0) {
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
        }
        
        requestLayout();
        
        return true;
    }
    
    private void settleOrUnsettleViews() {
        List<PageContentView> toBeSettled = new ArrayList<>();
        for (int i = 0 ; i < childViews.size(); i++) {
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
        this.adapter = adapter;
        notifyAdapterChanged();
    }
    
    public void notifyAdapterChanged() {
        childViews.clear();
        removeAllViewsInLayout();
        scale = initialScale;
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

    private boolean withinBoundsInDirectionOfTravel(Rect bounds, float deltaX, float deltaY, float velocityX, float velocityY) {
        switch (directionOfTravel(deltaX, deltaY, velocityX, velocityY)) {
            case MOVING_DIAGONALLY: return bounds.contains(0, 0);
            case MOVING_LEFT:       return bounds.left <= 0;
            case MOVING_RIGHT:      return bounds.right >= 0;
            case MOVING_UP:         return bounds.top <= 0;
            case MOVING_DOWN:       return bounds.bottom >= 0;
            default: throw new NoSuchElementException();
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
            if (scale == initialScale && !scrollMode) {
                if (listener.onScrollWithoutScaling(e1, e2, distanceX, distanceY)) {
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

    @Override
    public void onLongPress(MotionEvent e) {
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
        
        if (scrollMode) {
            switch (direction) {
                case MOVING_UP:
                    if (bounds.top >= 0) {
                        viewRightOrDown();
                        return true;
                    }
                    break;
                case MOVING_DOWN:
                    if (bounds.bottom <= 0) {
                        viewLeftOrUp();
                        return true;
                    }
                    break;
            }
        } else {
            switch (direction) {
                case MOVING_LEFT:
                    if (bounds.left >= 0) {
                        if (!keepScrollOffset) {
                            viewRightOrDown();
                        }
                        return true;
                    }
                    break;
                case MOVING_RIGHT:
                    if (bounds.right <= 0) {
                        if (!keepScrollOffset) {
                            viewLeftOrUp();
                        }
                        return true;
                    }
                    break;
            }
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
         * 1. initialScale이 아닐 경우에는, initialScale맞춰준다.
         *
         * 2. initialScale인 경우에는 아래의 방법에 따라 확대한다.
         *  - content width > content height : 두 쪽 보기 - (content width / 2 (한 쪽 content width)가 화면에 가득차도록 확대)
         *
         *  - content width < content height : 한 쪽 보기 - 1.5배 확대
         */
        float focusX = e.getX();
        float focusY = e.getY();
        float toScale;
        if (scale == initialScale) {
            float contentWidth = view.getWidth();
            float contentHeight = view.getHeight();

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
            toScale = initialScale;
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
            if (toScale == initialScale) {
                // 축소
                scaleBit = scale - ((scale - initialScale) * (i + 1) / animationCount);
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
            requestLayout();
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
            requestLayout();
            post(scrollProcessor);
        }
    }
    
    public void destroy() {
        for (int i = 0 ; i < childViews.size() ; i++) {
            PageContentView view = childViews.valueAt(i);
            view.clear();
        }
        
        childViews.clear();
        clearViewCache();
    }

    public void clearViewCache() {
        viewCache.clear();
    }

    private void keepScrollOffsetIfNeeded() {
        if (keepScrollOffset) {
            View view = childViews.get(currentIndex);
            if (view != null) {
                keptScrollOffset = new Point(view.getLeft(), view.getTop());
            }
        }
    }

    private int dipToPixel(int dip) {
        return Math.round(dip * getResources().getDisplayMetrics().density);
    }

    public void applyKeptScrollOffset(Point keptScrollOffset, float initialScale) {
        this.keptScrollOffset = keptScrollOffset;
        this.keepScrollOffset = true;
        this.initialScale = initialScale;
    }

    public float getScale() {
        return scale;
    }

    public Point getScrollOffset() {
        View view = childViews.get(currentIndex);
        return new Point(view.getLeft(), view.getTop());
    }
}
