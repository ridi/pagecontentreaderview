package com.ridi.books.viewer.reader.pagecontent;

import android.support.annotation.WorkerThread;

public interface PageContentProvider {
    int getPageContentCount();
    SizeF getPageContentSize(int index);
    @WorkerThread
    PageContent getPageContent(int index);
}
