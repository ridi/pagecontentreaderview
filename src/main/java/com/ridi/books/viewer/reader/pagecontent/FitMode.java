package com.ridi.books.viewer.reader.pagecontent;

/**
 * Created by kering on 2017. 5. 12..
 */
public enum FitMode {
    AUTO, WIDTH, WIDTH_70, WIDTH_50, HEIGHT;

    float calculateScale(SizeF contentSize, int targetWidth, int targetHeight) {
        switch (this) {
            case WIDTH:
                return targetWidth / contentSize.width;
            case WIDTH_70:
                return targetWidth * 0.7f / contentSize.width;
            case WIDTH_50:
                return targetWidth * 0.5f / contentSize.width;
            case HEIGHT:
                return targetHeight / contentSize.height;
            default:
            case AUTO:
                return Math.min(targetWidth / contentSize.width, targetHeight / contentSize.height);
        }
    }
}
