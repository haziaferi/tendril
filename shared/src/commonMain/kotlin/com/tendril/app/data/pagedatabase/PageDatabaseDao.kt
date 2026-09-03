package com.tendril.app.data.pagedatabase

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDatabaseDao {
    @Insert
    suspend fun insert(database: PageDatabase): Long

    @Update
    suspend fun update(database: PageDatabase)

    @Query("SELECT * FROM page_databases WHERE id = :id")
    suspend fun getById(id: Long): PageDatabase?

    /** Row-as-page (§5.1) — a Row's own `Page.databaseId` points at a `PageDatabase.id`
     * directly (not a Page id), so its property strip looks the binding up by this, not
     * [observeByPageId]. */
    @Query("SELECT * FROM page_databases WHERE id = :id")
    fun observeById(id: Long): Flow<PageDatabase?>

    @Query("SELECT * FROM page_databases WHERE pageId = :pageId")
    suspend fun getByPageId(pageId: Long): PageDatabase?

    @Query("SELECT * FROM page_databases WHERE pageId = :pageId")
    fun observeByPageId(pageId: Long): Flow<PageDatabase?>
}

@Dao
interface PropertyDao {
    @Insert
    suspend fun insert(property: Property): Long

    @Update
    suspend fun update(property: Property)

    @Query("DELETE FROM properties WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM properties WHERE databaseId = :databaseId ORDER BY `order`")
    fun observeForDatabase(databaseId: Long): Flow<List<Property>>

    @Query("SELECT * FROM properties WHERE databaseId = :databaseId ORDER BY `order`")
    suspend fun getForDatabase(databaseId: Long): List<Property>

    @Query("SELECT * FROM properties WHERE id = :id")
    suspend fun getById(id: Long): Property?

    /** §9.4 snapshot merge — a Row's [PropertyValue.propertyId] can reference a Property
     * defined by a *different* page's (the owning Database's) snapshot file, so resolving it
     * needs every device-wide uid→id mapping at once rather than one query per value. */
    @Query("SELECT * FROM properties")
    suspend fun getAll(): List<Property>
}

@Dao
interface PropertyValueDao {
    @Insert
    suspend fun insert(value: PropertyValue): Long

    @Update
    suspend fun update(value: PropertyValue)

    @Query("SELECT * FROM property_values WHERE rowPageId = :rowPageId")
    fun observeForRow(rowPageId: Long): Flow<List<PropertyValue>>

    @Query("SELECT * FROM property_values WHERE rowPageId = :rowPageId")
    suspend fun getForRow(rowPageId: Long): List<PropertyValue>

    @Query("SELECT * FROM property_values WHERE propertyId = :propertyId AND rowPageId = :rowPageId LIMIT 1")
    suspend fun getForPropertyAndRow(propertyId: Long, rowPageId: Long): PropertyValue?

    @Query("SELECT * FROM property_values WHERE propertyId = :propertyId")
    suspend fun getAllForProperty(propertyId: Long): List<PropertyValue>

    @Query("DELETE FROM property_values WHERE propertyId = :propertyId")
    suspend fun deleteAllForProperty(propertyId: Long)

    /** §9.4 snapshot merge — a winning Row record's cell values fully replace the local set. */
    @Query("DELETE FROM property_values WHERE rowPageId = :rowPageId")
    suspend fun deleteAllForRow(rowPageId: Long)

    /** Table view (§5.1) — every stored cell value across a whole Database in one Flow,
     * rather than one Flow per row, so the table recomposes from a single combined source. */
    @Query("SELECT pv.* FROM property_values pv INNER JOIN properties p ON p.id = pv.propertyId WHERE p.databaseId = :databaseId")
    fun observeForDatabase(databaseId: Long): Flow<List<PropertyValue>>
}

/** Room's `@Upsert` resolves conflicts on the primary key, not the `(propertyId, rowPageId)`
 * unique index this table actually keys cell identity on — a fresh [PropertyValue] always has
 * `id = 0`, so `@Upsert` would attempt an insert every time and hit that index's constraint
 * instead of updating. Explicit get-then-insert/update instead. */
suspend fun PropertyValueDao.setValue(propertyId: Long, rowPageId: Long, value: String?) {
    val existing = getForPropertyAndRow(propertyId, rowPageId)
    if (existing != null) update(existing.copy(value = value)) else insert(PropertyValue(propertyId = propertyId, rowPageId = rowPageId, value = value))
}

@Dao
interface PageDatabaseViewDao {
    @Insert
    suspend fun insert(view: PageDatabaseView): Long

    @Update
    suspend fun update(view: PageDatabaseView)

    @Query("DELETE FROM page_database_views WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM page_database_views WHERE databaseId = :databaseId ORDER BY `order`")
    fun observeForDatabase(databaseId: Long): Flow<List<PageDatabaseView>>

    /** §9.4 snapshot export — one-shot counterpart to [observeForDatabase]. */
    @Query("SELECT * FROM page_database_views WHERE databaseId = :databaseId ORDER BY `order`")
    suspend fun getForDatabase(databaseId: Long): List<PageDatabaseView>

    /** §9.4 snapshot merge — a winning Database record's view set fully replaces the local
     * one. Safe as a blind delete+reinsert (unlike [PropertyDao]'s properties): nothing
     * outside this table references a View's id, so there's no orphaned-data risk. */
    @Query("DELETE FROM page_database_views WHERE databaseId = :databaseId")
    suspend fun deleteAllForDatabase(databaseId: Long)

    /** One-shot ground truth for [PageDatabaseViewModel.ensureDefaultView]'s guard — the
     * `views` StateFlow's own `.value` isn't safe to check there: its initial value is
     * `emptyList()` until Room's Flow has actually emitted once, so reading it immediately on
     * first composition can race and insert a duplicate default view. */
    @Query("SELECT COUNT(*) FROM page_database_views WHERE databaseId = :databaseId")
    suspend fun countForDatabase(databaseId: Long): Int
}
