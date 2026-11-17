package com.subtracks.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("DROP TABLE IF EXISTS `app_settings`")
            connection.execSQL("ALTER TABLE `artists` ADD COLUMN `coverArt` TEXT")
        }
    }

val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `queue_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`position` INTEGER NOT NULL, `sourceId` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                    "`refId` TEXT NOT NULL, `rangeStart` INTEGER, `rangeEnd` INTEGER, " +
                    "FOREIGN KEY(`sourceId`) REFERENCES `sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_queue_entries_sourceId` ON `queue_entries` (`sourceId`)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_queue_entries_position` ON `queue_entries` (`position`)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `playback_cursor` (`id` INTEGER NOT NULL, " +
                    "`queuePosition` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
        }
    }

val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `artwork_seeds` (`cacheKey` TEXT NOT NULL, " +
                    "`primary` INTEGER NOT NULL, `secondary` INTEGER, " +
                    "PRIMARY KEY(`cacheKey`))",
            )
        }
    }

val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `playlists` ADD COLUMN `duration` INTEGER NOT NULL DEFAULT 0")
        }
    }

val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `playback_cursor` ADD COLUMN `shuffleEnabled` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE `playback_cursor` ADD COLUMN `repeatMode` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE `playback_cursor` ADD COLUMN `shuffleSeed` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `shuffle_order` (`sequence` INTEGER NOT NULL, " +
                    "`flatPosition` INTEGER NOT NULL, PRIMARY KEY(`sequence`))",
            )
        }
    }

val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `playback_cursor` ADD COLUMN `positionMs` INTEGER NOT NULL DEFAULT 0")
        }
    }

val MIGRATION_7_8 =
    object : Migration(7, 8) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `playlists` ADD COLUMN `changed` INTEGER NOT NULL DEFAULT 0")
        }
    }

val MIGRATION_8_9 =
    object : Migration(8, 9) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `songs` ADD COLUMN `created` INTEGER NOT NULL DEFAULT 0")
        }
    }

val MIGRATION_9_10 =
    object : Migration(9, 10) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("DROP TABLE IF EXISTS `search_index`")
        }
    }

val MIGRATION_10_11 =
    object : Migration(10, 11) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `discs` (`sourceId` INTEGER NOT NULL, `albumId` TEXT NOT NULL, " +
                    "`disc` INTEGER NOT NULL, `title` TEXT NOT NULL, PRIMARY KEY(`sourceId`, `albumId`, `disc`), " +
                    "FOREIGN KEY(`sourceId`, `albumId`) REFERENCES `albums`(`sourceId`, `id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_discs_sourceId_albumId` ON `discs` (`sourceId`, `albumId`)")
        }
    }

val MIGRATION_11_12 =
    object : Migration(11, 12) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `artwork_seeds` ADD COLUMN `nameBusy` INTEGER")
        }
    }

val MIGRATION_12_13 =
    object : Migration(12, 13) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("DROP TABLE IF EXISTS `artwork_seeds`")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `artwork_seeds` (`cacheKey` TEXT NOT NULL, " +
                    "`primary` INTEGER NOT NULL, `secondary` INTEGER, `nameBusy` INTEGER, " +
                    "`sourceId` INTEGER NOT NULL, `storedAt` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`cacheKey`), " +
                    "FOREIGN KEY(`sourceId`) REFERENCES `sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_artwork_seeds_sourceId` ON `artwork_seeds` (`sourceId`)")
        }
    }

val MIGRATION_13_14 =
    object : Migration(13, 14) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `albums` DROP COLUMN `frequentRank`")
            connection.execSQL("ALTER TABLE `albums` DROP COLUMN `recentRank`")
        }
    }

val MIGRATION_14_15 =
    object : Migration(14, 15) {
        override suspend fun migrate(connection: SQLiteConnection) {
            rebuildTable(connection, "albums", ALBUMS_V14, ALBUMS_V14_COPY, ALBUMS_V14_INDICES)
            rebuildTable(connection, "artists", ARTISTS_V14, ARTISTS_V14_COPY, ARTISTS_V14_INDICES)
            rebuildTable(connection, "playlists", PLAYLISTS_V14, PLAYLISTS_V14_COPY, PLAYLISTS_V14_INDICES)
        }
    }

val MIGRATION_15_16 =
    object : Migration(15, 16) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `up_next_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`position` INTEGER NOT NULL, `sourceId` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                    "`refId` TEXT NOT NULL, `rangeStart` INTEGER, `rangeEnd` INTEGER, " +
                    "FOREIGN KEY(`sourceId`) REFERENCES `sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_up_next_entries_sourceId` ON `up_next_entries` (`sourceId`)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_up_next_entries_position` ON `up_next_entries` (`position`)",
            )
            connection.execSQL("ALTER TABLE `playback_cursor` ADD COLUMN `upNextAnchor` INTEGER NOT NULL DEFAULT 0")
        }
    }

val MIGRATION_16_17 =
    object : Migration(16, 17) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `playback_cursor` ADD COLUMN `shuffleSize` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("DROP TABLE IF EXISTS `shuffle_order`")
        }
    }

private suspend fun rebuildTable(
    connection: SQLiteConnection,
    table: String,
    createSql: String,
    copySql: String,
    indices: List<String>,
) {
    val temp = "${table}_new"
    connection.execSQL(createSql.replace("\${TABLE_NAME}", temp))
    connection.execSQL(copySql.replace("\${TABLE_NAME}", temp))
    connection.execSQL("DROP TABLE `$table`")
    connection.execSQL("ALTER TABLE `$temp` RENAME TO `$table`")
    indices.forEach { connection.execSQL(it.replace("\${TABLE_NAME}", table)) }
}

private const val ALBUMS_V14 =
    "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`sourceId` INTEGER NOT NULL, `id` TEXT NOT NULL, `artistId` TEXT, " +
        "`name` TEXT NOT NULL COLLATE NOCASE, `albumArtist` TEXT COLLATE NOCASE, `created` INTEGER NOT NULL, " +
        "`coverArt` TEXT, `genre` TEXT, `year` INTEGER, `starred` INTEGER, `songCount` INTEGER NOT NULL, " +
        "PRIMARY KEY(`sourceId`, `id`), " +
        "FOREIGN KEY(`sourceId`) REFERENCES `sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"

private const val ALBUMS_V14_COPY =
    "INSERT INTO `\${TABLE_NAME}` (sourceId, id, artistId, name, albumArtist, created, coverArt, genre, year, starred, songCount) " +
        "SELECT sourceId, id, artistId, name, albumArtist, created, coverArt, genre, year, starred, songCount FROM `albums`"

private val ALBUMS_V14_INDICES =
    listOf(
        "CREATE INDEX IF NOT EXISTS `index_albums_sourceId` ON `\${TABLE_NAME}` (`sourceId`)",
        "CREATE INDEX IF NOT EXISTS `index_albums_sourceId_artistId` ON `\${TABLE_NAME}` (`sourceId`, `artistId`)",
        "CREATE INDEX IF NOT EXISTS `index_albums_name` ON `\${TABLE_NAME}` (`sourceId`, `name`, `id`)",
        "CREATE INDEX IF NOT EXISTS `index_albums_artist` ON `\${TABLE_NAME}` (`sourceId`, `albumArtist`, `year`, `name`, `id`)",
        "CREATE INDEX IF NOT EXISTS `index_albums_year` ON `\${TABLE_NAME}` (`sourceId` ASC, `year` DESC, `name` ASC, `id` ASC)",
        "CREATE INDEX IF NOT EXISTS `index_albums_added` ON `\${TABLE_NAME}` (`sourceId` ASC, `created` DESC, `name` ASC, `id` ASC)",
        "CREATE INDEX IF NOT EXISTS `index_albums_starred` ON `\${TABLE_NAME}` (`sourceId` ASC, `starred` DESC, `name` ASC, `id` ASC)",
    )

private const val ARTISTS_V14 =
    "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`sourceId` INTEGER NOT NULL, `id` TEXT NOT NULL, " +
        "`name` TEXT NOT NULL COLLATE NOCASE, `albumCount` INTEGER NOT NULL, `starred` INTEGER, `coverArt` TEXT, " +
        "PRIMARY KEY(`sourceId`, `id`), FOREIGN KEY(`sourceId`) REFERENCES `sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"

private const val ARTISTS_V14_COPY =
    "INSERT INTO `\${TABLE_NAME}` (sourceId, id, name, albumCount, starred, coverArt) " +
        "SELECT sourceId, id, name, albumCount, starred, coverArt FROM `artists`"

private val ARTISTS_V14_INDICES =
    listOf(
        "CREATE INDEX IF NOT EXISTS `index_artists_sourceId` ON `\${TABLE_NAME}` (`sourceId`)",
        "CREATE INDEX IF NOT EXISTS `index_artists_name` ON `\${TABLE_NAME}` (`sourceId`, `name`, `id`)",
        "CREATE INDEX IF NOT EXISTS `index_artists_albumCount` ON `\${TABLE_NAME}` (`sourceId` ASC, `albumCount` DESC, `name` ASC, `id` ASC)",
        "CREATE INDEX IF NOT EXISTS `index_artists_starred` ON `\${TABLE_NAME}` (`sourceId` ASC, `starred` DESC, `name` ASC, `id` ASC)",
    )

private const val PLAYLISTS_V14 =
    "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`sourceId` INTEGER NOT NULL, `id` TEXT NOT NULL, " +
        "`name` TEXT NOT NULL COLLATE NOCASE, `comment` TEXT, `coverArt` TEXT, `songCount` INTEGER NOT NULL, " +
        "`created` INTEGER NOT NULL, `changed` INTEGER NOT NULL DEFAULT 0, `duration` INTEGER NOT NULL DEFAULT 0, " +
        "PRIMARY KEY(`sourceId`, `id`), FOREIGN KEY(`sourceId`) REFERENCES `sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"

private const val PLAYLISTS_V14_COPY =
    "INSERT INTO `\${TABLE_NAME}` (sourceId, id, name, comment, coverArt, songCount, created, changed, duration) " +
        "SELECT sourceId, id, name, comment, coverArt, songCount, created, changed, duration FROM `playlists`"

private val PLAYLISTS_V14_INDICES =
    listOf(
        "CREATE INDEX IF NOT EXISTS `index_playlists_sourceId` ON `\${TABLE_NAME}` (`sourceId`)",
        "CREATE INDEX IF NOT EXISTS `index_playlists_name` ON `\${TABLE_NAME}` (`sourceId`, `name`, `id`)",
        "CREATE INDEX IF NOT EXISTS `index_playlists_added` ON `\${TABLE_NAME}` (`sourceId` ASC, `created` DESC, `name` ASC, `id` ASC)",
        "CREATE INDEX IF NOT EXISTS `index_playlists_updated` ON `\${TABLE_NAME}` (`sourceId` ASC, `changed` DESC, `name` ASC, `id` ASC)",
    )

val MIGRATION_17_18 =
    object : Migration(17, 18) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `song_downloads` (`sourceId` INTEGER NOT NULL, `songId` TEXT NOT NULL, " +
                    "`status` TEXT NOT NULL, `engineId` INTEGER, `bytes` INTEGER NOT NULL DEFAULT 0, " +
                    "`total` INTEGER NOT NULL DEFAULT 0, `error` TEXT, " +
                    "PRIMARY KEY(`sourceId`, `songId`), FOREIGN KEY(`sourceId`) REFERENCES `sources`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`sourceId`, `songId`) REFERENCES `songs`(`sourceId`, `id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_song_downloads_sourceId` ON `song_downloads` (`sourceId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_song_downloads_engineId` ON `song_downloads` (`engineId`)")
        }
    }

val MIGRATION_18_19 =
    object : Migration(18, 19) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_songs_sourceId_albumId_order` ON `songs` " +
                    "(`sourceId`, `albumId`, `disc`, `track`, `id`)",
            )
            // Covered by the prefix of the new index.
            connection.execSQL("DROP INDEX IF EXISTS `index_songs_sourceId_albumId`")
        }
    }

val MIGRATION_19_20 =
    object : Migration(19, 20) {
        override suspend fun migrate(connection: SQLiteConnection) {
            createSearchIndex(connection, "album_search", "albums", listOf("name", "albumArtist"))
            createSearchIndex(connection, "artist_search", "artists", listOf("name"))
            createSearchIndex(connection, "playlist_search", "playlists", listOf("name"))
        }
    }

val MIGRATION_20_21 =
    object : Migration(20, 21) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `songs` ADD COLUMN `playCount` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE `songs` ADD COLUMN `played` INTEGER")
            connection.execSQL("ALTER TABLE `albums` ADD COLUMN `playCount` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE `albums` ADD COLUMN `played` INTEGER")
            connection.execSQL("ALTER TABLE `artists` ADD COLUMN `playCount` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE `artists` ADD COLUMN `played` INTEGER")
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_albums_frequent` ON `albums` " +
                    "(`sourceId` ASC, `playCount` DESC, `name` ASC, `id` ASC)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_albums_recent` ON `albums` " +
                    "(`sourceId` ASC, `played` DESC, `name` ASC, `id` ASC)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_artists_frequent` ON `artists` " +
                    "(`sourceId` ASC, `playCount` DESC, `name` ASC, `id` ASC)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_artists_recent` ON `artists` " +
                    "(`sourceId` ASC, `played` DESC, `name` ASC, `id` ASC)",
            )
        }
    }

val MIGRATION_21_22 =
    object : Migration(21, 22) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `song_genres` (`sourceId` INTEGER NOT NULL, `songId` TEXT NOT NULL, " +
                    "`position` INTEGER NOT NULL, `genre` TEXT NOT NULL, " +
                    "PRIMARY KEY(`sourceId`, `songId`, `position`), " +
                    "FOREIGN KEY(`sourceId`, `songId`) REFERENCES `songs`(`sourceId`, `id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_song_genres_sourceId_genre` ON `song_genres` (`sourceId`, `genre`)",
            )
        }
    }

val MIGRATION_22_23 =
    object : Migration(22, 23) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_songs_starred` ON `songs` " +
                    "(`sourceId` ASC, `starred` DESC, `id` ASC)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_songs_genre` ON `songs` " +
                    "(`sourceId` ASC, `genre` ASC, `played` DESC)",
            )
            createSearchIndex(connection, "song_search", "songs", listOf("title", "artist"))
        }
    }

val MIGRATION_23_24 =
    object : Migration(23, 24) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "INSERT INTO `song_genres` (`sourceId`, `songId`, `position`, `genre`) " +
                    "SELECT `sourceId`, `id`, 0, trim(`genre`) FROM `songs` " +
                    "WHERE `genre` IS NOT NULL AND trim(`genre`) != '' " +
                    "AND NOT EXISTS (SELECT 1 FROM `song_genres` sg " +
                    "WHERE sg.`sourceId` = `songs`.`sourceId` AND sg.`songId` = `songs`.`id`)",
            )
        }
    }

val MIGRATION_24_25 =
    object : Migration(24, 25) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE `song_downloads` ADD COLUMN `downloadedAt` INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

val MIGRATION_25_26 =
    object : Migration(25, 26) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("DROP INDEX IF EXISTS `index_songs_genre`")
            connection.execSQL("ALTER TABLE `songs` DROP COLUMN `genre`")
        }
    }

val MIGRATION_26_27 =
    object : Migration(26, 27) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `pending_actions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`sourceId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `targetId` TEXT NOT NULL, " +
                    "`starType` TEXT, `time` INTEGER NOT NULL DEFAULT 0, " +
                    "FOREIGN KEY(`sourceId`) REFERENCES `sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_pending_actions_sourceId` ON `pending_actions` (`sourceId`)",
            )
        }
    }

val MIGRATION_27_28 =
    object : Migration(27, 28) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `songs` ADD COLUMN `trackGain` REAL")
            connection.execSQL("ALTER TABLE `songs` ADD COLUMN `albumGain` REAL")
            connection.execSQL("ALTER TABLE `songs` ADD COLUMN `trackPeak` REAL")
            connection.execSQL("ALTER TABLE `songs` ADD COLUMN `albumPeak` REAL")
        }
    }

val MIGRATION_28_29 =
    object : Migration(28, 29) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `songs` ADD COLUMN `artists` TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE `albums` ADD COLUMN `artists` TEXT NOT NULL DEFAULT ''")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `album_artists` (`sourceId` INTEGER NOT NULL, `albumId` TEXT NOT NULL, " +
                    "`position` INTEGER NOT NULL, `artistId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "PRIMARY KEY(`sourceId`, `albumId`, `position`), " +
                    "FOREIGN KEY(`sourceId`, `albumId`) REFERENCES `albums`(`sourceId`, `id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_album_artists_sourceId_artistId` ON `album_artists` (`sourceId`, `artistId`)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `song_artists` (`sourceId` INTEGER NOT NULL, `songId` TEXT NOT NULL, " +
                    "`position` INTEGER NOT NULL, `artistId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "PRIMARY KEY(`sourceId`, `songId`, `position`), " +
                    "FOREIGN KEY(`sourceId`, `songId`) REFERENCES `songs`(`sourceId`, `id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_song_artists_sourceId_artistId` ON `song_artists` (`sourceId`, `artistId`)",
            )
        }
    }

val MIGRATION_29_30 =
    object : Migration(29, 30) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `artists` ADD COLUMN `songCount` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL(
                "WITH credited AS (" +
                    "SELECT sourceId, artistId, songId FROM song_artists " +
                    "UNION " +
                    "SELECT sourceId, artistId, id AS songId FROM songs WHERE artistId IS NOT NULL " +
                    "UNION " +
                    "SELECT album_artists.sourceId, album_artists.artistId, songs.id " +
                    "FROM album_artists JOIN songs " +
                    "ON songs.sourceId = album_artists.sourceId AND songs.albumId = album_artists.albumId " +
                    "UNION " +
                    "SELECT albums.sourceId, albums.artistId, songs.id " +
                    "FROM albums JOIN songs ON songs.sourceId = albums.sourceId AND songs.albumId = albums.id " +
                    "WHERE albums.artistId IS NOT NULL" +
                    "), counts AS (" +
                    "SELECT sourceId, artistId AS id, COUNT(*) AS songCount FROM credited GROUP BY sourceId, artistId" +
                    ") " +
                    "UPDATE artists SET songCount = c.songCount " +
                    "FROM (SELECT artists.sourceId AS sourceId, artists.id AS id, " +
                    "COALESCE(counts.songCount, 0) AS songCount " +
                    "FROM artists LEFT JOIN counts " +
                    "ON counts.sourceId = artists.sourceId AND counts.id = artists.id) c " +
                    "WHERE artists.sourceId = c.sourceId AND artists.id = c.id " +
                    "AND artists.songCount != c.songCount",
            )
        }
    }

val MIGRATION_30_31 =
    object : Migration(30, 31) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `artists` ADD COLUMN `sortName` TEXT NOT NULL DEFAULT '' COLLATE NOCASE")
            connection.execSQL("ALTER TABLE `albums` ADD COLUMN `sortName` TEXT NOT NULL DEFAULT '' COLLATE NOCASE")
            connection.execSQL("ALTER TABLE `albums` ADD COLUMN `albumArtistSort` TEXT NOT NULL DEFAULT '' COLLATE NOCASE")
            connection.execSQL("ALTER TABLE `songs` ADD COLUMN `sortName` TEXT NOT NULL DEFAULT '' COLLATE NOCASE")
            connection.execSQL("ALTER TABLE `songs` ADD COLUMN `artistSort` TEXT NOT NULL DEFAULT '' COLLATE NOCASE")
            dropSearchIndex(connection, "artist_search")
            dropSearchIndex(connection, "album_search")
            dropSearchIndex(connection, "song_search")
            connection.execSQL("UPDATE `artists` SET `sortName` = `name`")
            connection.execSQL("UPDATE `albums` SET `sortName` = `name`")
            connection.execSQL("UPDATE `songs` SET `sortName` = `title`")
            connection.execSQL(
                "UPDATE `albums` SET `albumArtistSort` = COALESCE(" +
                    "(SELECT a.`sortName` FROM `artists` a " +
                    "WHERE a.`sourceId` = `albums`.`sourceId` AND a.`id` = `albums`.`artistId`), " +
                    "(SELECT a.`sortName` FROM `album_artists` aa JOIN `artists` a " +
                    "ON a.`sourceId` = aa.`sourceId` AND a.`id` = aa.`artistId` " +
                    "WHERE aa.`sourceId` = `albums`.`sourceId` AND aa.`albumId` = `albums`.`id` " +
                    "ORDER BY aa.`position` LIMIT 1), " +
                    "`albumArtist`, '')",
            )
            connection.execSQL(
                "UPDATE `songs` SET `artistSort` = COALESCE(" +
                    "(SELECT a.`sortName` FROM `artists` a " +
                    "WHERE a.`sourceId` = `songs`.`sourceId` AND a.`id` = `songs`.`artistId`), " +
                    "(SELECT a.`sortName` FROM `song_artists` sa JOIN `artists` a " +
                    "ON a.`sourceId` = sa.`sourceId` AND a.`id` = sa.`artistId` " +
                    "WHERE sa.`sourceId` = `songs`.`sourceId` AND sa.`songId` = `songs`.`id` " +
                    "ORDER BY sa.`position` LIMIT 1), " +
                    "`artist`, '')",
            )
            ARTIST_INDEXES.forEach { connection.execSQL("DROP INDEX IF EXISTS `$it`") }
            ALBUM_INDEXES.forEach { connection.execSQL("DROP INDEX IF EXISTS `$it`") }
            ARTIST_INDEX_SQL.forEach { connection.execSQL(it) }
            ALBUM_INDEX_SQL.forEach { connection.execSQL(it) }
            createSearchIndex(connection, "artist_search", "artists", listOf("name", "sortName"))
            createSearchIndex(connection, "album_search", "albums", listOf("name", "albumArtist", "sortName", "albumArtistSort"))
            createSearchIndex(connection, "song_search", "songs", listOf("title", "artist", "sortName", "artistSort"))
        }
    }

private val ARTIST_INDEXES =
    listOf("index_artists_name", "index_artists_albumCount", "index_artists_starred", "index_artists_frequent", "index_artists_recent")

private val ALBUM_INDEXES =
    listOf(
        "index_albums_name",
        "index_albums_artist",
        "index_albums_year",
        "index_albums_added",
        "index_albums_starred",
        "index_albums_frequent",
        "index_albums_recent",
    )

private val ARTIST_INDEX_SQL =
    listOf(
        "CREATE INDEX IF NOT EXISTS `index_artists_name` ON `artists` (`sourceId`, `sortName`, `id`)",
        "CREATE INDEX IF NOT EXISTS `index_artists_albumCount` ON `artists` " +
            "(`sourceId` ASC, `albumCount` DESC, `sortName` ASC, `id` ASC)",
        "CREATE INDEX IF NOT EXISTS `index_artists_starred` ON `artists` " +
            "(`sourceId` ASC, `starred` DESC, `sortName` ASC, `id` ASC)",
        "CREATE INDEX IF NOT EXISTS `index_artists_frequent` ON `artists` " +
            "(`sourceId` ASC, `playCount` DESC, `sortName` ASC, `id` ASC)",
        "CREATE INDEX IF NOT EXISTS `index_artists_recent` ON `artists` " +
            "(`sourceId` ASC, `played` DESC, `sortName` ASC, `id` ASC)",
    )

private val ALBUM_INDEX_SQL =
    listOf(
        "CREATE INDEX IF NOT EXISTS `index_albums_name` ON `albums` (`sourceId`, `sortName`, `id`)",
        "CREATE INDEX IF NOT EXISTS `index_albums_artist` ON `albums` " +
            "(`sourceId`, `albumArtistSort`, `year`, `sortName`, `id`)",
        "CREATE INDEX IF NOT EXISTS `index_albums_year` ON `albums` " +
            "(`sourceId` ASC, `year` DESC, `sortName` ASC, `id` ASC)",
        "CREATE INDEX IF NOT EXISTS `index_albums_added` ON `albums` " +
            "(`sourceId` ASC, `created` DESC, `sortName` ASC, `id` ASC)",
        "CREATE INDEX IF NOT EXISTS `index_albums_starred` ON `albums` " +
            "(`sourceId` ASC, `starred` DESC, `sortName` ASC, `id` ASC)",
        "CREATE INDEX IF NOT EXISTS `index_albums_frequent` ON `albums` " +
            "(`sourceId` ASC, `playCount` DESC, `sortName` ASC, `id` ASC)",
        "CREATE INDEX IF NOT EXISTS `index_albums_recent` ON `albums` " +
            "(`sourceId` ASC, `played` DESC, `sortName` ASC, `id` ASC)",
    )

private suspend fun dropSearchIndex(
    connection: SQLiteConnection,
    table: String,
) {
    listOf("BEFORE_UPDATE", "BEFORE_DELETE", "AFTER_UPDATE", "AFTER_INSERT").forEach {
        connection.execSQL("DROP TRIGGER IF EXISTS `room_fts_content_sync_${table}_$it`")
    }
    connection.execSQL("DROP TABLE IF EXISTS `$table`")
}

val MIGRATION_31_32 =
    object : Migration(31, 32) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `artists` ADD COLUMN `credited` INTEGER NOT NULL DEFAULT 1")
            connection.execSQL(
                "UPDATE `artists` SET `credited` = 0 " +
                    "WHERE EXISTS (SELECT 1 FROM `album_artists` WHERE `sourceId` = `artists`.`sourceId`) " +
                    "AND EXISTS (SELECT 1 FROM `song_artists` WHERE `sourceId` = `artists`.`sourceId`) " +
                    "AND NOT EXISTS (SELECT 1 FROM `album_artists` aa " +
                    "WHERE aa.`sourceId` = `artists`.`sourceId` AND aa.`artistId` = `artists`.`id`) " +
                    "AND NOT EXISTS (SELECT 1 FROM `song_artists` sa " +
                    "WHERE sa.`sourceId` = `artists`.`sourceId` AND sa.`artistId` = `artists`.`id`)",
            )
        }
    }

val MIGRATION_32_33 =
    object : Migration(32, 33) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `sources` ADD COLUMN `creditsStale` INTEGER NOT NULL DEFAULT 1")
            connection.execSQL("DROP INDEX IF EXISTS `index_songs_starred`")
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_songs_starred` ON `songs` " +
                    "(`sourceId` ASC, `starred` DESC, `sortName` ASC, `id` ASC)",
            )
        }
    }

// Recomputes the artist totals from the same credit unions the sync uses, so badges, the artist
// page and the shuffle queue agree for libraries that predate the change.
val MIGRATION_33_34 =
    object : Migration(33, 34) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "WITH credited_songs AS (" +
                    "SELECT songs.`sourceId` AS sourceId, songs.`artistId` AS artistId, " +
                    "songs.`id` AS songId, songs.`playCount` AS playCount, songs.`played` AS played " +
                    "FROM songs WHERE songs.`artistId` IS NOT NULL " +
                    "UNION " +
                    "SELECT song_artists.`sourceId`, song_artists.`artistId`, songs.`id`, " +
                    "songs.`playCount`, songs.`played` " +
                    "FROM song_artists JOIN songs " +
                    "ON songs.`sourceId` = song_artists.`sourceId` AND songs.`id` = song_artists.`songId` " +
                    "UNION " +
                    "SELECT albums.`sourceId`, albums.`artistId`, songs.`id`, " +
                    "songs.`playCount`, songs.`played` " +
                    "FROM albums JOIN songs " +
                    "ON songs.`sourceId` = albums.`sourceId` AND songs.`albumId` = albums.`id` " +
                    "WHERE albums.`artistId` IS NOT NULL " +
                    "UNION " +
                    "SELECT album_artists.`sourceId`, album_artists.`artistId`, songs.`id`, " +
                    "songs.`playCount`, songs.`played` " +
                    "FROM album_artists JOIN songs " +
                    "ON songs.`sourceId` = album_artists.`sourceId` AND songs.`albumId` = album_artists.`albumId`" +
                    "), credited_albums AS (" +
                    "SELECT albums.`sourceId` AS sourceId, albums.`artistId` AS artistId, " +
                    "albums.`id` AS albumId " +
                    "FROM albums WHERE albums.`artistId` IS NOT NULL " +
                    "UNION " +
                    "SELECT album_artists.`sourceId`, album_artists.`artistId`, albums.`id` " +
                    "FROM album_artists JOIN albums " +
                    "ON albums.`sourceId` = album_artists.`sourceId` AND albums.`id` = album_artists.`albumId` " +
                    "UNION " +
                    "SELECT songs.`sourceId`, songs.`artistId`, songs.`albumId` " +
                    "FROM songs JOIN albums " +
                    "ON albums.`sourceId` = songs.`sourceId` AND albums.`id` = songs.`albumId` " +
                    "WHERE songs.`artistId` IS NOT NULL " +
                    "UNION " +
                    "SELECT song_artists.`sourceId`, song_artists.`artistId`, songs.`albumId` " +
                    "FROM song_artists JOIN songs " +
                    "ON songs.`sourceId` = song_artists.`sourceId` AND songs.`id` = song_artists.`songId` " +
                    "JOIN albums ON albums.`sourceId` = songs.`sourceId` AND albums.`id` = songs.`albumId`" +
                    "), totals AS (" +
                    "SELECT sourceId, artistId AS id, COUNT(DISTINCT songId) AS songCount, " +
                    "SUM(playCount) AS playCount, MAX(played) AS played " +
                    "FROM credited_songs GROUP BY sourceId, artistId" +
                    "), album_totals AS (" +
                    "SELECT sourceId, artistId AS id, COUNT(DISTINCT albumId) AS albumCount " +
                    "FROM credited_albums GROUP BY sourceId, artistId" +
                    ") " +
                    "UPDATE artists SET songCount = c.songCount, albumCount = c.albumCount, " +
                    "playCount = c.playCount, played = c.played " +
                    "FROM (SELECT artists.`sourceId` AS sourceId, artists.`id` AS id, " +
                    "COALESCE(totals.songCount, 0) AS songCount, " +
                    "COALESCE(album_totals.albumCount, 0) AS albumCount, " +
                    "COALESCE(totals.playCount, 0) AS playCount, totals.played AS played " +
                    "FROM artists LEFT JOIN totals " +
                    "ON totals.sourceId = artists.`sourceId` AND totals.id = artists.`id` " +
                    "LEFT JOIN album_totals " +
                    "ON album_totals.sourceId = artists.`sourceId` AND album_totals.id = artists.`id` " +
                    "WHERE EXISTS (SELECT 1 FROM album_artists WHERE album_artists.`sourceId` = artists.`sourceId`) " +
                    "OR EXISTS (SELECT 1 FROM song_artists WHERE song_artists.`sourceId` = artists.`sourceId`)" +
                    ") c " +
                    "WHERE artists.`sourceId` = c.sourceId AND artists.`id` = c.id " +
                    "AND (artists.`songCount` != c.songCount OR artists.`albumCount` != c.albumCount " +
                    "OR artists.`playCount` != c.playCount OR artists.`played` IS NOT c.played)",
            )
        }
    }

val MIGRATION_34_35 =
    object : Migration(34, 35) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `artist_bios` (" +
                    "`sourceId` INTEGER NOT NULL, `artistId` TEXT NOT NULL, `biography` TEXT, " +
                    "PRIMARY KEY(`sourceId`, `artistId`), " +
                    "FOREIGN KEY(`sourceId`) REFERENCES `sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
        }
    }

// Mirrors the FTS5 table and content-sync triggers Room generates for the @Fts5 entities.
private suspend fun createSearchIndex(
    connection: SQLiteConnection,
    table: String,
    content: String,
    columns: List<String>,
) {
    connection.execSQL(
        "CREATE VIRTUAL TABLE IF NOT EXISTS `$table` USING FTS5(" +
            columns.joinToString(", ") { "`$it`" } + ", tokenize=`trigram`, content=`$content`)",
    )
    val inserted = (listOf("rowid") + columns).joinToString(", ") { "`$it`" }
    val values = columns.joinToString(", ") { "NEW.`$it`" }
    connection.execSQL(
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${table}_BEFORE_UPDATE BEFORE UPDATE ON `$content` " +
            "BEGIN DELETE FROM `$table` WHERE `rowid`=OLD.`rowid`; END",
    )
    connection.execSQL(
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${table}_BEFORE_DELETE BEFORE DELETE ON `$content` " +
            "BEGIN DELETE FROM `$table` WHERE `rowid`=OLD.`rowid`; END",
    )
    connection.execSQL(
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${table}_AFTER_UPDATE AFTER UPDATE ON `$content` " +
            "BEGIN INSERT INTO `$table`($inserted) VALUES (NEW.`rowid`, $values); END",
    )
    connection.execSQL(
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${table}_AFTER_INSERT AFTER INSERT ON `$content` " +
            "BEGIN INSERT INTO `$table`($inserted) VALUES (NEW.`rowid`, $values); END",
    )
    connection.execSQL("INSERT INTO `$table`(`$table`) VALUES('rebuild')")
}

val MIGRATION_35_36 =
    object : Migration(35, 36) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `artists` ADD COLUMN `artistImageUrl` TEXT")
            connection.execSQL("ALTER TABLE `artists` ADD COLUMN `coverArts` TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE `artists` ADD COLUMN `downloadedCoverArts` TEXT NOT NULL DEFAULT ''")
            optimizeArtistSearchTriggers(connection)
            backfillArtistArt(connection)
        }
    }

val MIGRATION_36_37 =
    object : Migration(36, 37) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_albums_name_plain` ON `albums` (`sourceId`, `name`, `id`)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_albums_artist_plain` " +
                    "ON `albums` (`sourceId`, `albumArtist`, `year`, `name`, `id`)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_artists_name_plain` ON `artists` (`sourceId`, `name`, `id`)",
            )
        }
    }

val MIGRATION_37_38 =
    object : Migration(37, 38) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `playlists` ADD COLUMN `owner` TEXT")
            connection.execSQL("ALTER TABLE `playlists` ADD COLUMN `shared` INTEGER NOT NULL DEFAULT 0")
        }
    }

// The ciphertext column only. Encrypting needs the Keystore, so SourceRepository moves each plaintext password
// across at startup, verifying the ciphertext before it clears the plaintext.
val MIGRATION_38_39 =
    object : Migration(38, 39) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `subsonic_sources` ADD COLUMN `passwordCiphertext` TEXT")
        }
    }

val MIGRATION_39_40 =
    object : Migration(39, 40) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `source_headers` (`sourceId` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                    "`name` TEXT NOT NULL, `valueCiphertext` TEXT NOT NULL, PRIMARY KEY(`sourceId`, `position`), " +
                    "FOREIGN KEY(`sourceId`) REFERENCES `sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_source_headers_sourceId` ON `source_headers` (`sourceId`)")
        }
    }

// Extended metadata (M2.2): MusicBrainz IDs, file format details, the compilation flag, and album labels and
// release types. All nullable or empty until the next sync fills them; nothing is backfilled.
val MIGRATION_40_41 =
    object : Migration(40, 41) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `artists` ADD COLUMN `musicBrainzId` TEXT")
            connection.execSQL("ALTER TABLE `albums` ADD COLUMN `musicBrainzId` TEXT")
            connection.execSQL("ALTER TABLE `albums` ADD COLUMN `compilation` INTEGER")
            listOf("musicBrainzId" to "TEXT", "suffix" to "TEXT", "bitRate" to "INTEGER", "bitDepth" to "INTEGER")
                .plus(listOf("samplingRate" to "INTEGER", "channelCount" to "INTEGER"))
                .forEach { (column, type) -> connection.execSQL("ALTER TABLE `songs` ADD COLUMN `$column` $type") }
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `album_labels` (`sourceId` INTEGER NOT NULL, `albumId` TEXT NOT NULL, " +
                    "`position` INTEGER NOT NULL, `name` TEXT NOT NULL COLLATE NOCASE, " +
                    "PRIMARY KEY(`sourceId`, `albumId`, `position`), FOREIGN KEY(`sourceId`, `albumId`) " +
                    "REFERENCES `albums`(`sourceId`, `id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_album_labels_sourceId_name` ON `album_labels` (`sourceId`, `name`)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `album_release_types` (`sourceId` INTEGER NOT NULL, `albumId` TEXT NOT NULL, " +
                    "`position` INTEGER NOT NULL, `type` TEXT NOT NULL, " +
                    "PRIMARY KEY(`sourceId`, `albumId`, `position`), FOREIGN KEY(`sourceId`, `albumId`) " +
                    "REFERENCES `albums`(`sourceId`, `id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
        }
    }

// The Library's Songs list (M2.4) sorts by title, artist and date added.
val MIGRATION_41_42 =
    object : Migration(41, 42) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_sortName` ON `songs` (`sourceId`, `sortName`, `id`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_artistSort` ON `songs` (`sourceId`, `artistSort`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_created` ON `songs` (`sourceId`, `created`, `id`)")
        }
    }

val MIGRATION_42_43 =
    object : Migration(42, 43) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS pins (sourceId INTEGER NOT NULL, kind TEXT NOT NULL, refId TEXT NOT NULL, " +
                    "position INTEGER NOT NULL, PRIMARY KEY(sourceId, kind, refId), " +
                    "FOREIGN KEY(sourceId) REFERENCES sources(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_pins_sourceId_position ON pins (sourceId, position)")
        }
    }

val MIGRATION_43_44 =
    object : Migration(43, 44) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS search_history (sourceId INTEGER NOT NULL, kind TEXT NOT NULL, " +
                    "refId TEXT NOT NULL, visitedAt INTEGER NOT NULL, PRIMARY KEY(sourceId, kind, refId), " +
                    "FOREIGN KEY(sourceId) REFERENCES sources(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_search_history_sourceId_visitedAt ON search_history (sourceId, visitedAt)",
            )
        }
    }

val MIGRATION_44_45 =
    object : Migration(44, 45) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE playback_cursor ADD COLUMN sourceLabel TEXT")
        }
    }

val MIGRATION_45_46 =
    object : Migration(45, 46) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE playback_cursor ADD COLUMN shuffleRemoved TEXT")
        }
    }

val MIGRATION_46_47 =
    object : Migration(46, 47) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS queue_orders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "sourceId INTEGER NOT NULL, prefix TEXT NOT NULL, " +
                    "FOREIGN KEY(sourceId) REFERENCES sources(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_queue_orders_sourceId ON queue_orders (sourceId)")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS queue_order_entries (orderId INTEGER NOT NULL, position INTEGER NOT NULL, " +
                    "sourceId INTEGER NOT NULL, kind TEXT NOT NULL, refId TEXT NOT NULL, rangeStart INTEGER, rangeEnd INTEGER, " +
                    "length INTEGER NOT NULL, PRIMARY KEY(orderId, position), " +
                    "FOREIGN KEY(orderId) REFERENCES queue_orders(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(sourceId) REFERENCES sources(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_queue_order_entries_sourceId ON queue_order_entries (sourceId)")
        }
    }

val MIGRATION_48_49 =
    object : Migration(48, 49) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS lyrics_cache (sourceId INTEGER NOT NULL, songId TEXT NOT NULL, " +
                    "provider TEXT NOT NULL, payload TEXT NOT NULL, fetchedAt INTEGER NOT NULL, " +
                    "PRIMARY KEY(sourceId, songId, provider), " +
                    "FOREIGN KEY(sourceId) REFERENCES sources(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
        }
    }

val MIGRATION_49_50 =
    object : Migration(49, 50) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS playlist_destinations (sourceId INTEGER NOT NULL, playlistId TEXT NOT NULL, " +
                    "usedAt INTEGER NOT NULL, PRIMARY KEY(sourceId, playlistId), " +
                    "FOREIGN KEY(sourceId) REFERENCES sources(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_playlist_destinations_sourceId_usedAt ON playlist_destinations (sourceId, usedAt)",
            )
        }
    }

val MIGRATION_50_51 =
    object : Migration(50, 51) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS playlist_save_drafts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "sourceId INTEGER NOT NULL, name TEXT NOT NULL, size INTEGER NOT NULL, playlistId TEXT, " +
                    "FOREIGN KEY(sourceId) REFERENCES sources(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_save_drafts_sourceId ON playlist_save_drafts (sourceId)")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS playlist_save_draft_songs (draftId INTEGER NOT NULL, position INTEGER NOT NULL, " +
                    "songId TEXT NOT NULL, PRIMARY KEY(draftId, position), " +
                    "FOREIGN KEY(draftId) REFERENCES playlist_save_drafts(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
        }
    }

val MIGRATION_51_52 =
    object : Migration(51, 52) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS quick_playlists (sourceId INTEGER NOT NULL, playlistId TEXT NOT NULL, " +
                    "position INTEGER NOT NULL, PRIMARY KEY(sourceId, playlistId), " +
                    "FOREIGN KEY(sourceId) REFERENCES sources(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_quick_playlists_sourceId_position ON quick_playlists (sourceId, position)",
            )
        }
    }

/**
 * Records download collections. Lists already fully downloaded (every song has a download row) are recorded so
 * their songs keep the new protection; partial lists and artists are not guessed.
 */
val MIGRATION_52_53 =
    object : Migration(52, 53) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS download_collections (sourceId INTEGER NOT NULL, kind TEXT NOT NULL, " +
                    "refId TEXT NOT NULL, PRIMARY KEY(sourceId, kind, refId), " +
                    "FOREIGN KEY(sourceId) REFERENCES sources(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            connection.execSQL(
                "INSERT OR IGNORE INTO download_collections (sourceId, kind, refId) " +
                    "SELECT p.sourceId, 'Playlist', p.id FROM playlists p " +
                    "WHERE EXISTS (SELECT 1 FROM playlist_songs ps WHERE ps.sourceId = p.sourceId AND ps.playlistId = p.id) " +
                    "AND NOT EXISTS (SELECT 1 FROM playlist_songs ps WHERE ps.sourceId = p.sourceId AND ps.playlistId = p.id " +
                    "AND NOT EXISTS (SELECT 1 FROM song_downloads d WHERE d.sourceId = ps.sourceId AND d.songId = ps.songId))",
            )
            connection.execSQL(
                "INSERT OR IGNORE INTO download_collections (sourceId, kind, refId) " +
                    "SELECT a.sourceId, 'Album', a.id FROM albums a " +
                    "WHERE EXISTS (SELECT 1 FROM songs s WHERE s.sourceId = a.sourceId AND s.albumId = a.id) " +
                    "AND NOT EXISTS (SELECT 1 FROM songs s WHERE s.sourceId = a.sourceId AND s.albumId = a.id " +
                    "AND NOT EXISTS (SELECT 1 FROM song_downloads d WHERE d.sourceId = s.sourceId AND d.songId = s.id))",
            )
        }
    }

val MIGRATION_53_54 =
    object : Migration(53, 54) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE playback_cursor ADD COLUMN positionSongId TEXT")
        }
    }

val MIGRATION_54_55 =
    object : Migration(54, 55) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `play_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`sourceId` INTEGER NOT NULL, `songId` TEXT NOT NULL, `playedAt` INTEGER NOT NULL, " +
                    "`title` TEXT NOT NULL, `artist` TEXT, `album` TEXT, `durationMs` INTEGER, " +
                    "FOREIGN KEY(`sourceId`) REFERENCES `sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_play_events_sourceId_playedAt` ON `play_events` (`sourceId`, `playedAt`)",
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_play_events_playedAt` ON `play_events` (`playedAt`)")
        }
    }

val MIGRATION_55_56 =
    object : Migration(55, 56) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `pending_listens` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`listenedAt` INTEGER NOT NULL, `trackName` TEXT NOT NULL, `artistName` TEXT NOT NULL, " +
                    "`releaseName` TEXT, `durationMs` INTEGER)",
            )
        }
    }

val MIGRATION_56_57 =
    object : Migration(56, 57) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `similar_songs` (`sourceId` INTEGER NOT NULL, `seedKind` TEXT NOT NULL, " +
                    "`seedId` TEXT NOT NULL, `position` INTEGER NOT NULL, `songId` TEXT NOT NULL, `artistId` TEXT, " +
                    "`fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`sourceId`, `seedKind`, `seedId`, `position`), " +
                    "FOREIGN KEY(`sourceId`) REFERENCES `sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_similar_songs_sourceId` ON `similar_songs` (`sourceId`)")
        }
    }

val MIGRATION_57_58 =
    object : Migration(57, 58) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE playback_cursor ADD COLUMN extensionStart INTEGER")
        }
    }

/** The fresh-releases feed: its stored result, refresh times and MusicBrainz name lookups, per server. */
val MIGRATION_58_59 =
    object : Migration(58, 59) {
        override suspend fun migrate(connection: SQLiteConnection) {
            val cascade = "FOREIGN KEY(`sourceId`) REFERENCES `sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE"
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `fresh_releases` (`sourceId` INTEGER NOT NULL, `id` TEXT NOT NULL, " +
                    "`position` INTEGER NOT NULL, `title` TEXT NOT NULL, `artistName` TEXT NOT NULL, `firstReleaseDate` TEXT, " +
                    "`typeLabel` TEXT NOT NULL, PRIMARY KEY(`sourceId`, `id`), $cascade )",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_fresh_releases_sourceId_position` ON `fresh_releases` (`sourceId`, `position`)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `fresh_release_state` (`sourceId` INTEGER NOT NULL, `refreshedAt` INTEGER, " +
                    "`attemptedAt` INTEGER NOT NULL, PRIMARY KEY(`sourceId`), $cascade )",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `musicbrainz_names` (`sourceId` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                    "`musicBrainzId` TEXT, `triedAt` INTEGER NOT NULL, PRIMARY KEY(`sourceId`, `name`), $cascade )",
            )
        }
    }

val MIGRATION_47_48 =
    object : Migration(47, 48) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE playback_cursor ADD COLUMN shuffleLinear INTEGER NOT NULL DEFAULT 0")
        }
    }

internal fun artistSearchTriggersOptimized(connection: SQLiteConnection): Boolean {
    val triggers = mutableListOf<String>()
    connection.prepare("SELECT sql FROM sqlite_master WHERE type = 'trigger' AND name IN (?, ?)").use { statement ->
        statement.bindText(1, "room_fts_content_sync_artist_search_BEFORE_UPDATE")
        statement.bindText(2, "room_fts_content_sync_artist_search_AFTER_UPDATE")
        while (statement.step()) triggers += statement.getText(0)
    }
    return triggers.size == 2 && triggers.all { it.contains("UPDATE OF `name`, `sortName`") }
}

internal fun optimizeArtistSearchTriggers(connection: SQLiteConnection) {
    connection.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_artist_search_BEFORE_UPDATE")
    connection.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_artist_search_AFTER_UPDATE")
    connection.execSQL(
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_artist_search_BEFORE_UPDATE " +
            "BEFORE UPDATE OF `name`, `sortName` ON `artists` BEGIN " +
            "DELETE FROM `artist_search` WHERE `rowid`=OLD.`rowid`; END",
    )
    connection.execSQL(
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_artist_search_AFTER_UPDATE " +
            "AFTER UPDATE OF `name`, `sortName` ON `artists` BEGIN " +
            "INSERT INTO `artist_search`(`rowid`, `name`, `sortName`) " +
            "VALUES (NEW.`rowid`, NEW.`name`, NEW.`sortName`); END",
    )
}

private suspend fun backfillArtistArt(connection: SQLiteConnection) {
    val sourceIds = mutableListOf<Long>()
    connection.prepare("SELECT id FROM sources").use { statement ->
        while (statement.step()) sourceIds += statement.getLong(0)
    }
    sourceIds.forEach { sourceId ->
        connection.prepare(ARTIST_ART_UPDATE_SQL).use { statement ->
            statement.bindLong(1, sourceId)
            statement.step()
        }
    }
}

val MIGRATION_59_60 =
    object : Migration(59, 60) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `similar_artists_cache` (`sourceId` INTEGER NOT NULL, " +
                    "`artistId` TEXT NOT NULL, `payload` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`sourceId`, `artistId`), " +
                    "FOREIGN KEY(`sourceId`) REFERENCES `sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
        }
    }

val MIGRATIONS: Array<Migration> =
    arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19,
        MIGRATION_19_20,
        MIGRATION_20_21,
        MIGRATION_21_22,
        MIGRATION_22_23,
        MIGRATION_23_24,
        MIGRATION_24_25,
        MIGRATION_25_26,
        MIGRATION_26_27,
        MIGRATION_27_28,
        MIGRATION_28_29,
        MIGRATION_29_30,
        MIGRATION_30_31,
        MIGRATION_31_32,
        MIGRATION_32_33,
        MIGRATION_33_34,
        MIGRATION_34_35,
        MIGRATION_35_36,
        MIGRATION_36_37,
        MIGRATION_37_38,
        MIGRATION_38_39,
        MIGRATION_39_40,
        MIGRATION_40_41,
        MIGRATION_41_42,
        MIGRATION_42_43,
        MIGRATION_43_44,
        MIGRATION_44_45,
        MIGRATION_45_46,
        MIGRATION_46_47,
        MIGRATION_47_48,
        MIGRATION_48_49,
        MIGRATION_49_50,
        MIGRATION_50_51,
        MIGRATION_51_52,
        MIGRATION_52_53,
        MIGRATION_53_54,
        MIGRATION_54_55,
        MIGRATION_55_56,
        MIGRATION_56_57,
        MIGRATION_57_58,
        MIGRATION_58_59,
        MIGRATION_59_60,
    )
