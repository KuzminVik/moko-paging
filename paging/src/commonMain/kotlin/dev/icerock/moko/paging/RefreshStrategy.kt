package dev.icerock.moko.paging

enum class RefreshStrategy {
    /**
     * "Smart" merge.
     * Loads the first page and tries to prepend new items to the list.
     * Ignores items that are already in the list (even if their data changed).
     * Old pages (2, 3...) stay in memory.
     *
     * Suitable for: news feeds, logs, infinite streams.
     */
    MergeNewItems,

    /**
     * Full reload.
     * Loads the first page and COMPLETELY replaces the current list.
     * Ensures data freshness. Resets pagination to the start.
     *
     * Suitable for: product catalogs, request lists, bank transactions,
     * any lists where item data can change.
     */
    ReplaceEverything
}
