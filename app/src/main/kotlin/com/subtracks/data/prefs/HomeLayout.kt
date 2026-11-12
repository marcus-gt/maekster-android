package com.subtracks.data.prefs

enum class HomeShelf {
    Pinned,
    YourPlaylists,
    FavoriteAlbums,
    RecentlyAddedAlbums,
    RecentlyPlayedArtists,
    RecentlyStarredSongs,
    MostPlayedAlbums,
    Genres,
    RecentlyPlayedAlbums,
    MostPlayedArtists,
    Decades,
    Rediscover,
    ;

    val supportsGrid: Boolean get() =
        this in
            setOf(Pinned, YourPlaylists, FavoriteAlbums, RecentlyAddedAlbums, MostPlayedAlbums, RecentlyPlayedAlbums, Rediscover)
}

data class HomeLayout(
    val order: List<HomeShelf> = HomeShelf.entries,
    val hidden: Set<HomeShelf> = emptySet(),
    val grids: Set<HomeShelf> = emptySet(),
) {
    fun normalized(): HomeLayout {
        val result = order.distinct().toMutableList()
        for (shelf in HomeShelf.entries) {
            if (shelf in result) continue
            val previous = HomeShelf.entries.take(shelf.ordinal).lastOrNull { it in result }
            result.add(if (previous == null) 0 else result.indexOf(previous) + 1, shelf)
        }
        return copy(order = result, grids = grids.filter { it.supportsGrid }.toSet())
    }

    val visible: List<HomeShelf> get() = normalized().order.filter { it !in hidden }

    fun move(
        shelf: HomeShelf,
        offset: Int,
    ): HomeLayout {
        val items = normalized().order.toMutableList()
        val from = items.indexOf(shelf)
        val to = (from + offset).coerceIn(items.indices)
        items.add(to, items.removeAt(from))
        return copy(order = items)
    }

    fun show(
        shelf: HomeShelf,
        visible: Boolean,
    ): HomeLayout = copy(hidden = if (visible) hidden - shelf else hidden + shelf)

    fun grid(
        shelf: HomeShelf,
        enabled: Boolean,
    ): HomeLayout = copy(grids = if (enabled && shelf.supportsGrid) grids + shelf else grids - shelf)

    fun encode(): String =
        normalized().order.joinToString(",") { it.name } + "|" + hidden.sortedBy { it.ordinal }.joinToString(",") { it.name } +
            "|" + normalized().grids.sortedBy { it.ordinal }.joinToString(",") { it.name }

    companion object {
        fun decode(value: String?): HomeLayout {
            if (value == null) return HomeLayout()
            val parts = value.split("|")
            if (parts.size !in 2..3) return HomeLayout()

            fun shelves(part: String) = part.split(",").mapNotNull { name -> HomeShelf.entries.firstOrNull { it.name == name } }
            return HomeLayout(shelves(parts[0]), shelves(parts[1]).toSet(), shelves(parts.getOrElse(2) { "" }).toSet()).normalized()
        }
    }
}
