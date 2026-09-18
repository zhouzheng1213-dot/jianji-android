package com.jianji.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val TX_SELECT = """
SELECT t.id AS id,
       t.type AS type,
       t.amountCents AS amountCents,
       t.categoryId AS categoryId,
       t.accountId AS accountId,
       t.toAccountId AS toAccountId,
       t.dateEpochDay AS dateEpochDay,
       t.note AS note,
       t.createdAt AS createdAt,
       t.updatedAt AS updatedAt,
       c.name AS categoryName,
       c.icon AS categoryIcon,
       c.colorHex AS categoryColor,
       a.name AS accountName,
       a2.name AS toAccountName
FROM transactions t
LEFT JOIN categories c ON c.id = t.categoryId
LEFT JOIN accounts a ON a.id = t.accountId
LEFT JOIN accounts a2 ON a2.id = t.toAccountId
"""

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY type, sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type AND hidden = 0 ORDER BY sortOrder, id")
    fun observeVisible(type: Int): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun find(id: Long): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM categories WHERE type = :type")
    suspend fun maxSortOrder(type: Int): Int

    @Insert
    suspend fun insert(item: CategoryEntity): Long

    @Insert
    suspend fun insertAll(items: List<CategoryEntity>)

    @Update
    suspend fun update(item: CategoryEntity)

    @Query("UPDATE categories SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("DELETE FROM categories WHERE id = :id AND builtIn = 0")
    suspend fun deleteCustom(id: Long)
}

@Dao
interface AccountDao {

    @Query(
        """
        SELECT a.id AS id,
               a.name AS name,
               a.type AS type,
               a.icon AS icon,
               a.initialBalanceCents AS initialBalanceCents,
               a.initialBalanceCents
                 + COALESCE((SELECT SUM(t.amountCents) FROM transactions t WHERE t.accountId = a.id AND t.type = 1), 0)
                 - COALESCE((SELECT SUM(t.amountCents) FROM transactions t WHERE t.accountId = a.id AND t.type = 0), 0)
                 - COALESCE((SELECT SUM(t.amountCents) FROM transactions t WHERE t.accountId = a.id AND t.type = 2), 0)
                 + COALESCE((SELECT SUM(t.amountCents) FROM transactions t WHERE t.toAccountId = a.id AND t.type = 2), 0)
                 AS balanceCents
        FROM accounts a
        WHERE a.archived = 0
        ORDER BY a.sortOrder, a.id
        """
    )
    fun observeWithBalance(): Flow<List<AccountWithBalance>>

    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY archived, sortOrder, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun find(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY sortOrder, id LIMIT 1")
    suspend fun first(): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM accounts")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(item: AccountEntity): Long

    @Insert
    suspend fun insertAll(items: List<AccountEntity>)

    @Update
    suspend fun update(item: AccountEntity)

    @Query("UPDATE accounts SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)
}

@Dao
interface TransactionDao {

    @Query("$TX_SELECT WHERE t.dateEpochDay BETWEEN :from AND :to ORDER BY t.dateEpochDay DESC, t.createdAt DESC, t.id DESC")
    fun observeRange(from: Long, to: Long): Flow<List<TxRow>>

    @Query(
        """
        $TX_SELECT
        WHERE t.dateEpochDay BETWEEN :from AND :to
          AND (:type = -1 OR t.type = :type)
          AND (:categoryId = -1 OR t.categoryId = :categoryId)
          AND (:keyword = '' OR t.note LIKE '%' || :keyword || '%'
               OR c.name LIKE '%' || :keyword || '%'
               OR a.name LIKE '%' || :keyword || '%')
          AND (:minCents = -1 OR t.amountCents >= :minCents)
          AND (:maxCents = -1 OR t.amountCents <= :maxCents)
        ORDER BY t.dateEpochDay DESC, t.createdAt DESC, t.id DESC
        """
    )
    fun search(
        from: Long,
        to: Long,
        type: Int,
        categoryId: Long,
        keyword: String,
        minCents: Long,
        maxCents: Long
    ): Flow<List<TxRow>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun find(id: Long): TransactionEntity?

    /**
     * 收支合计。只取 0=支出 / 1=收入：转账(2) 只是钱在账户之间搬家，
     * 计进来会让「本月结余」被凭空放大。
     */
    @Query(
        """
        SELECT type AS type, SUM(amountCents) AS totalCents
        FROM transactions
        WHERE dateEpochDay BETWEEN :from AND :to
          AND type IN (0, 1)
        GROUP BY type
        """
    )
    fun observeTypeTotals(from: Long, to: Long): Flow<List<TypeTotal>>

    /** 按天 + 类型聚合，同样排除转账，保证每日柱状图的刻度就是真实收支。 */
    @Query(
        """
        SELECT dateEpochDay AS dateEpochDay, type AS type, SUM(amountCents) AS totalCents
        FROM transactions
        WHERE dateEpochDay BETWEEN :from AND :to
          AND type IN (0, 1)
        GROUP BY dateEpochDay, type
        ORDER BY dateEpochDay
        """
    )
    fun observeDayTotals(from: Long, to: Long): Flow<List<DayTotal>>

    @Query(
        """
        SELECT t.categoryId AS categoryId,
               c.name AS name,
               c.icon AS icon,
               c.colorHex AS colorHex,
               SUM(t.amountCents) AS totalCents,
               COUNT(*) AS txCount
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.type = :type AND t.dateEpochDay BETWEEN :from AND :to
        GROUP BY t.categoryId, c.name, c.icon, c.colorHex
        ORDER BY totalCents DESC
        """
    )
    fun observeCategoryStats(type: Int, from: Long, to: Long): Flow<List<CategoryStatRow>>

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countByCategory(categoryId: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId OR toAccountId = :accountId")
    suspend fun countByAccount(accountId: Long): Int

    @Insert
    suspend fun insert(item: TransactionEntity): Long

    @Update
    suspend fun update(item: TransactionEntity)

    @Delete
    suspend fun delete(item: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}
