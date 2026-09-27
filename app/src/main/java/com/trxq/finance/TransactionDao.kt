package com.trxq.finance.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<Transaction>>

    @Insert
    suspend fun insert(item: Transaction)

    @Insert
    suspend fun insertAll(items: List<Transaction>)

    @Update
    suspend fun update(item: Transaction)

    @Delete
    suspend fun delete(item: Transaction)

    @Query("DELETE FROM transactions")
    suspend fun clear()
}
