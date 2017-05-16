package com.ridi.books.viewer.reader.pagecontent;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

public abstract class PageContentViewAdapter extends BaseAdapter {
    private final Context context;
    
    public PageContentViewAdapter(Context context) {
        this.context = context;
    }

    @Override
    public int getCount() {
        return getPageContentProvider().getPageContentCount();
    }
    
    @Override
    public Object getItem(int position) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public long getItemId(int position) {
        return 0;
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        PageContentView view;
        if (convertView == null) {
            view = new PageContentView(context, parent.getWidth(), parent.getHeight(),
                    getFitMode(), getBackgroundTaskListener(), getBitmapPostProcessors());
        } else {
            view = (PageContentView) convertView;
        }
        view.loadPageContent(getPageContentProvider(), position);
        return view;
    }

    SizeF getPageContentSize(int position) {
        return getPageContentProvider().getPageContentSize(position);
    }

    protected abstract PageContentProvider getPageContentProvider();

    protected abstract FitMode getFitMode();

    protected abstract BackgroundTaskListener getBackgroundTaskListener();

    protected abstract BitmapPostProcessor[] getBitmapPostProcessors();
}
