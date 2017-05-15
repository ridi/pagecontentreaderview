package com.ridi.books.viewer.reader.pagecontent;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

public class PageContentViewAdapter extends BaseAdapter {
    private final Context context;
    private PageContentProvider provider;
    private FitMode fitMode;
    private final BackgroundTaskListener backgroundTaskListener;
    private final BitmapPostProcessor[] postProcessors;

    
    public PageContentViewAdapter(Context context, PageContentProvider provider,
                                  FitMode fitMode, BackgroundTaskListener backgroundTaskListener,
                                  BitmapPostProcessor... postProcessors) {
        this.context = context;
        this.provider = provider;
        this.fitMode = fitMode;
        this.backgroundTaskListener = backgroundTaskListener;
        this.postProcessors = postProcessors;
    }
    
    public void setPageContentProvider(PageContentProvider provider) {
        this.provider = provider;
        notifyDataSetChanged();
    }

    public void setFitMode(FitMode fitMode) {
        this.fitMode = fitMode;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return provider.getPageContentCount();
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
                    fitMode, backgroundTaskListener, postProcessors);
        } else {
            view = (PageContentView) convertView;
        }
        view.loadPageContent(provider, position);
        return view;
    }

    public SizeF getPageContentSize(int position) {
        return provider.getPageContentSize(position);
    }
}
