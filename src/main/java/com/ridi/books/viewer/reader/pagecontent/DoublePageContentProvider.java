package com.ridi.books.viewer.reader.pagecontent;

public class DoublePageContentProvider implements PageContentProvider {
    private final PageContentProvider singleProvider;
    private boolean singleOnFirstPage;    // 첫페이지는 싱글?
    private boolean reverseMode;          // 역방향
    private final boolean useDummyContent;
    private final DoublePageContent.SizePolicy sizePolicy;
    
    public DoublePageContentProvider(PageContentProvider singleProvider,
                                     boolean reverseMode, boolean useDummyContent,
                                     DoublePageContent.SizePolicy sizePolicy) {
        this.singleProvider = singleProvider;
        this.reverseMode = reverseMode;
        this.useDummyContent = useDummyContent;
        this.sizePolicy = sizePolicy;
    }
    
    public boolean isSingleOnFirstPage() {
        return singleOnFirstPage;
    }
    
    public void setSingleOnFirstPage(boolean singleOnFirstPage) {
        this.singleOnFirstPage = singleOnFirstPage;
    }

    public boolean isReverseMode() {
        return reverseMode;
    }

    public void setReverseMode(boolean reverseMode) {
        this.reverseMode = reverseMode;
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
    public SizeF getPageContentSize(int index) {
        if (index < 0 || index >= getPageContentCount()) {
            return null;
        }

        int leftIndex = getLeftPageIndex(index);
        int rightIndex = getRightPageIndex(index);
        SizeF leftSize = null, rightSize = null;
        if (leftIndex >= 0 && leftIndex < singleProvider.getPageContentCount()) {
            leftSize = singleProvider.getPageContentSize(leftIndex);
            if (leftSize == null) {
                return null;
            }
        }
        if (rightIndex >= 0 && rightIndex < singleProvider.getPageContentCount()) {
            rightSize = singleProvider.getPageContentSize(rightIndex);
            if (rightSize == null) {
                return null;
            }
        }
        if (leftSize == null && rightSize == null) {
            return null;
        } else if (leftSize == null) {
            if (useDummyContent) {
                leftSize = rightSize;
            } else {
                return rightSize;
            }
        } else if (rightSize == null) {
            if (useDummyContent) {
                rightSize = leftSize;
            } else {
                return leftSize;
            }
        }
        return sizePolicy.computeSize(leftSize, rightSize);
    }

    @Override
    public PageContent getPageContent(int index) {
        if (index < 0 || index >= getPageContentCount()) {
            return null;
        }

        int leftIndex = getLeftPageIndex(index);
        int rightIndex = getRightPageIndex(index);
        PageContent leftPage = null, rightPage = null;
        if (leftIndex >= 0 && leftIndex < singleProvider.getPageContentCount()) {
            leftPage = singleProvider.getPageContent(leftIndex);
            if (leftPage == null) {
                return null;
            }
        }
        if (rightIndex >= 0 && rightIndex < singleProvider.getPageContentCount()) {
            rightPage = singleProvider.getPageContent(rightIndex);
            if (rightPage == null) {
                return null;
            }
        }
        if (leftPage == null && rightPage == null) {
            return null;
        } else if (leftPage == null) {
            if (useDummyContent) {
                leftPage = new DummyPageContent(rightPage);
            } else {
                return rightPage;
            }
        } else if (rightPage == null) {
            if (useDummyContent) {
                rightPage = new DummyPageContent(leftPage);
            } else {
                return leftPage;
            }
        }
        return new DoublePageContent(leftPage, rightPage, sizePolicy);
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
