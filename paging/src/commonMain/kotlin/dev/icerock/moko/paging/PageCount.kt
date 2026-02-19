package dev.icerock.moko.paging

import kotlin.math.ceil

/**
 * Возвращает количество страниц, необходимых для вывода всех элементов.
 *
 * @param currentListSize Количество элементов (может быть null)
 * @param pageSize Размер одной страницы (должен быть > 0)
 * @return Количество страниц (целое неотрицательное число)
 */
fun calculateNextPage(
    currentListSize: Int?,
    pageSize: Int,
): Int {
    // Если список пустой или размер неподходящий, сразу возвращаем 0 страниц
    if (currentListSize == null || currentListSize == 0 || pageSize <= 0) return 0

    return ceil(currentListSize.toDouble() / pageSize).toInt()
}
