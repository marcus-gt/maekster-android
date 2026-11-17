package com.subtracks.data.prefs

enum class DiscoverShelf { FreshReleases, Stations, SmartShuffle, QueueBuilder }

data class DiscoverLayout(
    val order: List<DiscoverShelf> = DiscoverShelf.entries,
    val hidden: Set<DiscoverShelf> = emptySet(),
) {
    fun normalized(): DiscoverLayout {
        val items = order.distinct().toMutableList()
        for (shelf in DiscoverShelf.entries) {
            if (shelf in items) continue
            val before = DiscoverShelf.entries.take(shelf.ordinal).lastOrNull { it in items }
            items.add(if (before == null) 0 else items.indexOf(before) + 1, shelf)
        }
        return copy(order = items)
    }

    val visible: List<DiscoverShelf> get() = normalized().order.filter { it !in hidden }

    fun encode(): String =
        normalized().order.joinToString(",") { it.name } + "|" + hidden.sortedBy { it.ordinal }.joinToString(",") { it.name }

    companion object {
        fun decode(value: String?): DiscoverLayout {
            val parts = value?.split("|") ?: return DiscoverLayout()
            if (parts.size != 2) return DiscoverLayout()

            fun shelves(part: String) = part.split(",").mapNotNull { name -> DiscoverShelf.entries.firstOrNull { it.name == name } }
            return DiscoverLayout(shelves(parts[0]), shelves(parts[1]).toSet()).normalized()
        }
    }
}
