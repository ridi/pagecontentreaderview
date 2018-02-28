package com.ridi.books.viewer.reader.pagecontent;

public interface DoublePageSizePolicy {
    SizeF computeSize(SizeF leftSize, SizeF rightSize);

    enum Presets implements DoublePageSizePolicy {
        SMALLER_FIT() {
            @Override
            public SizeF computeSize(SizeF leftSize, SizeF rightSize) {
                return new SizeF(Math.min(leftSize.width, rightSize.width) * 2,
                        Math.min(leftSize.height, rightSize.height));
            }
        },
        LARGER_FIT() {
            @Override
            public SizeF computeSize(SizeF leftSize, SizeF rightSize) {
                return new SizeF(Math.max(leftSize.width, rightSize.width) * 2,
                        Math.max(leftSize.height, rightSize.height));
            }
        }
    }
}
