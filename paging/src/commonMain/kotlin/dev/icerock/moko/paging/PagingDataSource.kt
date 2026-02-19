package dev.icerock.moko.paging

/**
 * Интерфейс датасурса для Pagination
 */
interface PagingDataSource<Item> {
    /**
     * Метод проверки полная ли страница загружена (чтобы понять достигли ли мы конца списка)
     */
    fun isPageFull(list: List<Item>): Boolean

    /**
     * Метод загрузки страницы на основе текущих данных
     *
     * @param currentList загруженные элементы списка
     *
     * Возвращаемое значение - следующая страница
     * */
    suspend fun loadPage(currentList: List<Item>?): List<Item>
}

/**
 * Имплементация интерфейса PagingDataSource для постраничной загрузки через page/pageSize
 *
 * @param pageSize размер страницы
 * @param loadPage suspend метод для постраничной загрузки списка
 * */
@Suppress("FunctionName")
fun <Item> PageSizePagingDataSource(
    pageSize: Int,
    loadPage: suspend (page: Int, pageSize: Int) -> List<Item>
): PagingDataSource<Item> {
    return object : PagingDataSource<Item> {
        override fun isPageFull(list: List<Item>): Boolean {
            return list.size == pageSize
        }

        override suspend fun loadPage(currentList: List<Item>?): List<Item> {
            val page: Int = calculateNextPage(
                currentListSize = currentList?.size,
                pageSize = pageSize
            )

            return loadPage(page, pageSize)
        }
    }
}
