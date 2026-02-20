package dev.icerock.moko.paging

import kotlin.math.ceil

/**
 * Returns the number of pages required to display all items.
 *
 * @param currentListSize number of items (can be null)
 * @param pageSize size of a single page (must be > 0)
 * @return number of pages (a non-negative integer)
 */
fun calculateNextPage(
    currentListSize: Int?,
    pageSize: Int,
): Int {
    // Если список пустой или размер неподходящий, сразу возвращаем 0 страниц
    if (currentListSize == null || currentListSize == 0 || pageSize <= 0) return 0

    return ceil(currentListSize.toDouble() / pageSize).toInt()
}
