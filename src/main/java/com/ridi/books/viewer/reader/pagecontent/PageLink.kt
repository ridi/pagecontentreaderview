package com.ridi.books.viewer.reader.pagecontent

import android.graphics.RectF
import android.net.Uri

data class PageLink(val action: LinkAction, val target: Uri, var boundingRect: RectF)
