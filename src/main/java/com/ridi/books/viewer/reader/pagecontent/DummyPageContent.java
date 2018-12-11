package com.ridi.books.viewer.reader.pagecontent;

import android.graphics.Bitmap;

import java.util.Collections;
import java.util.List;

// 두쪽보기에서 한 페이지밖에 없을때 존재하지 않는 페이지를 위한 dummy content
// 나머지 한 페이지와 같은 크기를 갖도록 함
class DummyPageContent implements PageContent {
    private final SizeF size;
    
    DummyPageContent(PageContent sizeReference) {
        this.size = sizeReference.getSize();
    }

    @Override
    public SizeF getSize() {
        return size;
    }

    @Override
    public Bitmap renderToBitmap(int bitmapWidth, int bitmapHeight,
            int startX, int startY, int pageWidth, int pageHeight, boolean forHighQuality) {
        return null;
    }

    @Override
    public List<PageLink> getPageLinkList() {
        return Collections.emptyList();
    }
}
