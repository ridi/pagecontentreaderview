package com.ridi.books.viewer.reader.pagecontent;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

public class PageContentViewAdapter extends BaseAdapter {
    private PageContentProvider provider;
    private PageContentViewFactory viewFactory;
    
    public PageContentViewAdapter(PageContentProvider provider, PageContentViewFactory viewFactory) {
        this.provider = provider;
        this.viewFactory = viewFactory;
    }
    
    public void setPageContentProvider(PageContentProvider provider) {
        this.provider = provider;
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
            view = viewFactory.createContentView(
                    new PageContentView.Size(parent.getWidth(), parent.getHeight()));
        } else {
            view = (PageContentView) convertView;
        }
        
        view.loadPageContent(provider, position);
        
        return view;
    }
    
    public interface PageContentViewFactory {
        PageContentView createContentView(PageContentView.Size canvasSize);
    }
}
