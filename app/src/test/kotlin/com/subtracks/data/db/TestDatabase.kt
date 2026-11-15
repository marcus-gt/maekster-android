package com.subtracks.data.db

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider

fun createTestDatabase(driver: SQLiteDriver = BundledSQLiteDriver()): SubtracksDatabase {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return Room
        .inMemoryDatabaseBuilder(context, SubtracksDatabase::class.java)
        .setDriver(driver)
        .addSubtracksDatabaseCallbacks()
        .build()
}

/**
 * A file-backed database with the same pooled connections as the app: separate read-only reader connections
 * and one writer. An in-memory database has a single connection, so it cannot catch a write sent to a reader.
 */
fun createFileTestDatabase(name: String = "file-test.db"): SubtracksDatabase {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.deleteDatabase(name)
    return Room
        .databaseBuilder<SubtracksDatabase>(context, context.getDatabasePath(name).absolutePath)
        .setDriver(BundledSQLiteDriver())
        .addSubtracksDatabaseCallbacks()
        .build()
}
