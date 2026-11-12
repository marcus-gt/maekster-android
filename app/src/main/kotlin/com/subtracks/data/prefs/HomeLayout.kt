package com.subtracks.data.prefs

enum class HomeShelf {
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
}

data class HomeLayout(
    val order: List<HomeShelf> = HomeShelf.entries,
    val hidden: Set<HomeShelf> = emptySet(),
) {
    fun normalized(): HomeLayout {
        val result = order.distinct().toMutableList()
        for (shelf in HomeShelf.entries) {
            if (shelf in result) continue
            val previous = HomeShelf.entries.take(shelf.ordinal).lastOrNull { it in result }
            result.add(if (previous == null) 0 else result.indexOf(previous) + 1, shelf)
        }
        return copy(order = result)
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

    fun encode(): String =
        normalized().order.joinToString(",") { it.name } + "|" + hidden.sortedBy { it.ordinal }.joinToString(",") { it.name }

    companion object {
        fun decode(value: String?): HomeLayout {
            if (value == null) return HomeLayout()
            val parts = value.split("|")
            if (parts.size != 2) return HomeLayout()

            fun shelves(part: String) = part.split(",").mapNotNull { name -> HomeShelf.entries.firstOrNull { it.name == name } }
            return HomeLayout(shelves(parts[0]), shelves(parts[1]).toSet()).normalized()
        }
    }
}
