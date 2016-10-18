package com.ridi.books.viewer.reader.pagecontent;

public class DoublePageContentProvider implements PageContentProvider {
    private PageContentProvider singleProvider;
    private boolean singleOnFirstPage;    // 첫페이지는 싱글?
    private boolean reverseMode;          // 역방향
    private boolean useDummyContent;
    
    public DoublePageContentProvider(PageContentProvider singleProvider,
                                     boolean reverseMode, boolean useDummyContent) {
        this.singleProvider = singleProvider;
        this.reverseMode = reverseMode;
        this.useDummyContent = useDummyContent;
    }
    
    public boolean isSingleOnFirstPage() {
        return singleOnFirstPage;
    }
    
    public void setSingleOnFirstPage(boolean singleOnFirstPage) {
        this.singleOnFirstPage = singleOnFirstPage;
    }
    
    @Override
    public int getPageContentCount() {
        int singleCount = singleProvider.getPageContentCount();
        int count = singleCount / 2 + singleCount % 2;
        if (singleOnFirstPage && singleCount % 2 == 0) {
            count++;
        }
        return count;
    }
    
    @Override
    public PageContent getPageContent(int index) {
        if (index < 0 || index >= getPageContentCount()) {
            return null;
        }
        return DoublePageContent.getPageContent(
                singleProvider, getLeftPageIndex(index), getRightPageIndex(index), useDummyContent);
    }
    
    public int getLeftPageIndex(int index) {
        int pageIndex;
        
        if (reverseMode) {
            pageIndex = index * 2;
            if (!singleOnFirstPage) {
                pageIndex++;
            }
        } else {
            pageIndex = index * 2;
            if (singleOnFirstPage) {
                pageIndex--;
            }
        }
        return pageIndex;
    }
    
    public int getRightPageIndex(int index) {
        int pageIndex;
        
        if (reverseMode) {
            pageIndex = index * 2;
            if (singleOnFirstPage) {
                pageIndex--;
            }
        } else {
            pageIndex = index * 2;
            if (!singleOnFirstPage) {
                pageIndex++;
            }
        }
        
        return pageIndex;
    }
}
