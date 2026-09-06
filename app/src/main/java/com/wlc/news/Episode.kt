package com.wlc.news

import java.io.File

data class Episode(
    val filename: String,
    val remotePath: String,
    val sizeBytes: Long,
    val localFile: File,
    val date: String,
) {
    val downloaded: Boolean
        get() = localFile.exists() && localFile.length() == sizeBytes

    val displaySize: String
        get() {
            val mb = sizeBytes / 1024.0 / 1024.0
            return if (mb >= 1) String.format("%.1f MB", mb) else String.format("%.0f KB", sizeBytes / 1024.0)
        }
}
