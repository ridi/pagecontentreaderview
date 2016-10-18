package com.ridi.books.viewer.reader.pagecontent;

import android.support.annotation.WorkerThread;

public interface PageContentProvider {
    int getPageContentCount();
    @WorkerThread
    PageContent getPageContent(int index);
}
