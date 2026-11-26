package com.taller.sublimapro.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "workshop_config")
data class WorkshopConfigEntity(
    @PrimaryKey val id: Int = 1,
    val workshopName: String = "Mi Taller Creativo",
    val localCurrencySymbol: String = "Bs.",
    val exchangeRate: Double = 45.0,
    val electricityCostPerKwhUsd: Double = 0.05,
    val pressPowerWatts: Double = 1800.0,
    val teflonWearPerPressUsd: Double = 0.02,
    val inkCostPerMlUsd: Double = 0.08,
    val thermalTapeCostPerPieceUsd: Double = 0.03,
    val designRatePerMinuteUsd: Double = 0.15,
    val operatorRatePerMinuteUsd: Double = 0.10,
    val defaultWastePercent: Double = 5.0,
    val defaultMarginPercent: Double = 40.0
)

@Entity(tableName = "calculation_records")
data class CalculationRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productName: String,
    val technique: String,
    val widthCm: Double,
    val heightCm: Double,
    val quantity: Int,
    val blankCostUsd: Double,
    val unitCostUsd: Double,
    val unitPriceUsd: Double,
    val exchangeRateApplied: Double,
    val currencySymbol: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface WorkshopDao {
    @Query("SELECT * FROM workshop_config WHERE id = 1")
    fun observeConfig(): Flow<WorkshopConfigEntity?>

    @Query("SELECT * FROM workshop_config WHERE id = 1")
    suspend fun getConfigOnce(): WorkshopConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: WorkshopConfigEntity)

    @Query("SELECT * FROM calculation_records ORDER BY createdAt DESC")
    fun observeRecords(): Flow<List<CalculationRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: CalculationRecordEntity)

    @Delete
    suspend fun deleteRecord(record: CalculationRecordEntity)
}

@Database(
    entities = [WorkshopConfigEntity::class, CalculationRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workshopDao(): WorkshopDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sublimapro_taller_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
