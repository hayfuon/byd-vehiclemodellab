package com.haystudio.vehiclelab

internal fun settingsChapterScrollTarget(rowTop: Int, rowBottom: Int, current: Int,
    viewportHeight: Int, maximum: Int, edge: Int): Int {
    if (viewportHeight <= 0 || rowBottom <= rowTop) return current
    if (rowTop >= current && rowBottom <= current + viewportHeight) return current
    val inset = edge.coerceIn(0, viewportHeight / 4)
    val target = when {
        rowBottom - rowTop > viewportHeight - inset * 2 -> rowTop - inset
        rowTop < current + inset -> rowTop - inset
        rowBottom > current + viewportHeight - inset -> rowBottom - viewportHeight + inset
        else -> current
    }
    return target.coerceIn(0, maximum.coerceAtLeast(0))
}
