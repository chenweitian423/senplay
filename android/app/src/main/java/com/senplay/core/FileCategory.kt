package com.senplay.core

/**
 * The file-type groups a listing can be filtered by.
 *
 * The reference app has a switch per group (`kFileTypeShowVideo`, `kFileTypeShowMusic`,
 * `kFileTypeShowPicture`, `kFileTypeShowSubtitle`, `kFileTypeShowOther`). A listing has
 * only the name to go on before a file is opened, so the extension decides. The
 * extension sets match the iOS side, so the two platforms filter alike.
 */
enum class FileCategory(val key: String, val title: String, private val extensions: Set<String>) {
    VIDEO(
        "list.fileType.video", "视频",
        setOf(
            "mp4", "mkv", "mov", "m4v", "avi", "flv", "ts", "webm", "mpg", "mpeg",
            "rmvb", "rm", "wmv", "3gp", "m2ts", "iso", "asf", "vob", "ogv"
        )
    ),
    MUSIC(
        "list.fileType.music", "音乐",
        setOf("mp3", "flac", "aac", "wav", "m4a", "ogg", "ape", "wma", "opus", "aiff")
    ),
    PICTURE(
        "list.fileType.picture", "图片",
        setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "tiff")
    ),
    SUBTITLE(
        "list.fileType.subtitle", "字幕",
        setOf("srt", "ass", "ssa", "vtt", "sub", "idx", "sup", "smi", "ttml")
    ),
    OTHER("list.fileType.other", "其他", emptySet());

    companion object {
        fun of(name: String): FileCategory {
            val extension = name.substringAfterLast('.', "").lowercase()
            if (extension.isEmpty()) return OTHER
            return values().firstOrNull { extension in it.extensions } ?: OTHER
        }

        /** The groups the viewer has left switched on. */
        fun enabled(store: LibraryStore): Set<FileCategory> =
            values().filter { store.prefBool(it.key, true) }.toSet()
    }
}
