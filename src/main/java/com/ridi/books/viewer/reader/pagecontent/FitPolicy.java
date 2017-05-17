package com.ridi.books.viewer.reader.pagecontent;

/**
 * Created by kering on 2017. 5. 12..
 */
public interface FitPolicy {
    float calculateScale(int canvasWidth, int canvasHeight, SizeF contentSize);

    enum Presets implements FitPolicy {
        PAGE() {
            @Override
            public float calculateScale(int canvasWidth, int canvasHeight, SizeF contentSize) {
                return Math.min(canvasWidth / contentSize.width, canvasHeight / contentSize.height);
            }
        },
        WIDTH() {
            @Override
            public float calculateScale(int canvasWidth, int canvasHeight, SizeF contentSize) {
                return canvasWidth / contentSize.width;
            }
        },
        HEIGHT() {
            @Override
            public float calculateScale(int canvasWidth, int canvasHeight, SizeF contentSize) {
                return canvasHeight / contentSize.height;
            }
        }
    }
}
