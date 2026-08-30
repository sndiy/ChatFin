package androidx.room.util

import androidx.collection.ArrayMap

/**
 * Polyfill for Room KMP Desktop/JVM code generation bug (Google Issue 352482325).
 * Room compiler generates calls to `recursiveFetchArrayMap` for `@Relation` queries,
 * which is omitted from the room-runtime-jvm artifact.
 */
@Suppress("UNUSED_PARAMETER")
fun <K : Any, V : Any> recursiveFetchArrayMap(
    map: ArrayMap<K, V>,
    isMultiline: Boolean,
    fetchBlock: (ArrayMap<K, V>) -> Unit
) {
    val entries = map.entries.toList()
    val chunkSize = 999
    for (i in entries.indices step chunkSize) {
        val subMap = ArrayMap<K, V>()
        val end = minOf(i + chunkSize, entries.size)
        for (j in i until end) {
            val entry = entries[j]
            subMap[entry.key] = entry.value
        }
        fetchBlock(subMap)
    }
}
