package com.example.data.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import com.example.data.model.AccountTransaction
import com.example.data.model.Alert
import com.example.data.model.AppSetting
import com.example.data.model.PendingOrder
import com.example.data.model.SymbolState
import com.example.data.model.Trade
import com.example.data.model.TriggerHistory
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY createdAt DESC")
    fun getAllAlertsFlow(): Flow<List<Alert>>

    @Query("SELECT * FROM alerts")
    suspend fun getAllAlerts(): List<Alert>

    @Query("SELECT * FROM alerts WHERE symbol = :symbol AND isActive = 1")
    suspend fun getActiveAlertsForSymbol(symbol: String): List<Alert>

    @Query("SELECT * FROM alerts WHERE id = :id")
    suspend fun getAlertById(id: Int): Alert?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: Alert): Long

    @Update
    suspend fun updateAlert(alert: Alert)

    @Delete
    suspend fun deleteAlert(alert: Alert)

    @Query("DELETE FROM alerts WHERE id = :id")
    suspend fun deleteAlertById(id: Int)

    @Query("UPDATE alerts SET isActive = :isActive WHERE id = :id")
    suspend fun updateAlertActiveStatus(id: Int, isActive: Boolean)
    
    @Query("UPDATE alerts SET cooldownUntil = :cooldownUntil WHERE id = :id")
    suspend fun updateAlertCooldown(id: Int, cooldownUntil: Long?)
    
    @Query("DELETE FROM alerts")
    suspend fun deleteAllAlerts()
}

@Dao
interface TriggerHistoryDao {
    @Query("SELECT * FROM trigger_history ORDER BY triggeredAt DESC")
    fun getAllHistoryFlow(): Flow<List<TriggerHistory>>

    @Query("SELECT * FROM trigger_history ORDER BY triggeredAt DESC")
    suspend fun getAllHistory(): List<TriggerHistory>

    @Query("SELECT * FROM trigger_history WHERE alertId = :alertId ORDER BY triggeredAt DESC")
    fun getHistoryForAlert(alertId: Int): Flow<List<TriggerHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: TriggerHistory): Long

    @Query("DELETE FROM trigger_history WHERE alertId = :alertId")
    suspend fun deleteHistoryForAlert(alertId: Int)

    @Query("DELETE FROM trigger_history")
    suspend fun clearAllHistory()
}

@Dao
interface SymbolStateDao {
    @Query("SELECT * FROM symbol_states ORDER BY orderIndex ASC")
    fun getAllSymbolStatesFlow(): Flow<List<SymbolState>>

    @Query("SELECT * FROM symbol_states ORDER BY orderIndex ASC")
    suspend fun getAllSymbolStates(): List<SymbolState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSymbolStates(states: List<SymbolState>)

    @Update
    suspend fun updateSymbolState(state: SymbolState)
}

@Dao
interface AppSettingDao {
    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    suspend fun getSetting(key: String): AppSetting?

    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    fun getSettingFlow(key: String): Flow<AppSetting?>

    @Query("SELECT * FROM app_settings")
    suspend fun getAll(): List<AppSetting>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: AppSetting)

    @Query("DELETE FROM app_settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)

    @Query("DELETE FROM app_settings")
    suspend fun clearSettings()
}

@Dao
interface AppLogDao {
    @Query("SELECT * FROM app_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAllLogsFlow(): Flow<List<com.example.data.model.AppLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: com.example.data.model.AppLog): Long

    @Query("SELECT * FROM app_logs ORDER BY timestamp DESC")
    suspend fun getAllLogsForExport(): List<com.example.data.model.AppLog>

    @Query("SELECT COUNT(*) FROM app_logs")
    suspend fun getLogCount(): Int

    @Query("DELETE FROM app_logs WHERE id NOT IN (SELECT id FROM app_logs ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun deleteOldestLogsExcept(keepCount: Int)

    @Query("DELETE FROM app_logs WHERE id NOT IN (SELECT id FROM app_logs ORDER BY timestamp DESC LIMIT 100)")
    suspend fun pruneOldLogs()

    @Query("DELETE FROM app_logs")
    suspend fun clearAllLogs()
}

@Dao
interface TradeDao {
    @Query("SELECT * FROM trades WHERE status = 'OPEN' ORDER BY openTime DESC")
    fun getOpenTradesFlow(): Flow<List<Trade>>

    @Query("SELECT * FROM trades WHERE status = 'CLOSED' ORDER BY closeTime DESC")
    fun getClosedTradesFlow(): Flow<List<Trade>>

    @Query("SELECT * FROM trades WHERE status = 'OPEN'")
    suspend fun getOpenTrades(): List<Trade>

    @Query("SELECT * FROM trades WHERE symbol = :symbol AND status = 'OPEN'")
    suspend fun getOpenTradesForSymbol(symbol: String): List<Trade>

    @Query("SELECT * FROM trades ORDER BY openTime DESC")
    suspend fun getAllTradesForExport(): List<Trade>

    @Query("SELECT * FROM trades WHERE id = :id")
    suspend fun getById(id: Int): Trade?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trade: Trade): Long

    @Update
    suspend fun update(trade: Trade)

    @Query("DELETE FROM trades WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM trades")
    suspend fun deleteAll()
}

@Dao
interface PendingOrderDao {
    @Query("SELECT * FROM pending_orders WHERE status = 'PENDING' ORDER BY createdAt DESC")
    fun getPendingOrdersFlow(): Flow<List<PendingOrder>>

    @Query("SELECT * FROM pending_orders ORDER BY createdAt DESC")
    fun getAllOrdersFlow(): Flow<List<PendingOrder>>

    @Query("SELECT * FROM pending_orders WHERE status = 'PENDING'")
    suspend fun getActiveOrders(): List<PendingOrder>

    @Query("SELECT * FROM pending_orders WHERE symbol = :symbol AND status = 'PENDING'")
    suspend fun getActiveOrdersForSymbol(symbol: String): List<PendingOrder>

    @Query("SELECT * FROM pending_orders ORDER BY createdAt DESC")
    suspend fun getAllOrdersForExport(): List<PendingOrder>

    @Query("SELECT * FROM pending_orders WHERE id = :id")
    suspend fun getById(id: Int): PendingOrder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(order: PendingOrder): Long

    @Update
    suspend fun update(order: PendingOrder)

    @Query("DELETE FROM pending_orders WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM pending_orders")
    suspend fun deleteAll()
}

@Dao
interface AccountTransactionDao {
    @Query("SELECT * FROM account_transactions ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<AccountTransaction>>

    @Query("SELECT * FROM account_transactions ORDER BY timestamp DESC")
    suspend fun getAllForExport(): List<AccountTransaction>

    @Query("SELECT balanceAfter FROM account_transactions ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun getLatestBalance(): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: AccountTransaction): Long

    @Query("DELETE FROM account_transactions")
    suspend fun deleteAll()
}

/** v2 → v3: add the virtual-trading tables without touching existing data. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trades` (" +
                "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "`symbol` TEXT NOT NULL, `side` TEXT NOT NULL, `lots` REAL NOT NULL, " +
                "`entryPrice` REAL NOT NULL, `exitPrice` REAL, `stopLoss` REAL, `takeProfit` REAL, " +
                "`openTime` INTEGER NOT NULL, `closeTime` INTEGER, `realizedPnl` REAL NOT NULL, " +
                "`status` TEXT NOT NULL, `closedBy` TEXT, `originOrderId` INTEGER, `marginUsd` REAL NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pending_orders` (" +
                "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "`symbol` TEXT NOT NULL, `side` TEXT NOT NULL, `orderKind` TEXT NOT NULL, `lots` REAL NOT NULL, " +
                "`targetPrice` REAL NOT NULL, `stopLoss` REAL, `takeProfit` REAL, " +
                "`createdAt` INTEGER NOT NULL, `executedAt` INTEGER, `status` TEXT NOT NULL, " +
                "`closedBy` TEXT, `resultingTradeId` INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `account_transactions` (" +
                "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "`type` TEXT NOT NULL, `amount` REAL NOT NULL, `balanceAfter` REAL NOT NULL, " +
                "`note` TEXT NOT NULL, `relatedTradeId` INTEGER, `timestamp` INTEGER NOT NULL)"
        )
    }
}

@Database(
    entities = [
        Alert::class, TriggerHistory::class, SymbolState::class, AppSetting::class,
        com.example.data.model.AppLog::class,
        Trade::class, PendingOrder::class, AccountTransaction::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao
    abstract fun triggerHistoryDao(): TriggerHistoryDao
    abstract fun symbolStateDao(): SymbolStateDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun appLogDao(): AppLogDao
    abstract fun tradeDao(): TradeDao
    abstract fun pendingOrderDao(): PendingOrderDao
    abstract fun accountTransactionDao(): AccountTransactionDao
}
