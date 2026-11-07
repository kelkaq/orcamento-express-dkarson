package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.ClientDao
import com.example.data.dao.CompanyProfileDao
import com.example.data.dao.ProductServiceDao
import com.example.data.dao.QuoteDao
import com.example.data.dao.PaymentDao
import com.example.data.dao.QuoteSequenceDao
import com.example.data.entity.ClientEntity
import com.example.data.entity.CompanyProfileEntity
import com.example.data.entity.ProductServiceEntity
import com.example.data.entity.QuoteEntity
import com.example.data.entity.QuoteItemEntity
import com.example.data.entity.QuoteSequenceEntity
import com.example.data.entity.PaymentEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CompanyProfileEntity::class,
        ClientEntity::class,
        ProductServiceEntity::class,
        QuoteEntity::class,
        QuoteItemEntity::class,
        QuoteSequenceEntity::class,
        PaymentEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun companyProfileDao(): CompanyProfileDao
    abstract fun clientDao(): ClientDao
    abstract fun productServiceDao(): ProductServiceDao
    abstract fun paymentDao(): PaymentDao
    abstract fun quoteDao(): QuoteDao
    abstract fun quoteSequenceDao(): QuoteSequenceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `quote_sequences` (`year` INTEGER NOT NULL, `lastSequence` INTEGER NOT NULL, PRIMARY KEY(`year`))"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `payments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `quoteId` INTEGER NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `paidAt` INTEGER NOT NULL,
                        `method` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`quoteId`) REFERENCES `quotes`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_quoteId` ON `payments` (`quoteId`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "orcamento_express.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                INSTANCE?.let { database ->
                                    // Initialize clean, neutral company profile with zero mock/fake data
                                    database.companyProfileDao().upsertProfile(
                                        CompanyProfileEntity(
                                            id = 1,
                                            tradeName = "",
                                            legalName = "",
                                            document = "",
                                            phone = "",
                                            email = "",
                                            address = "",
                                            pixKey = "",
                                            pixKeyType = "Chave",
                                            defaultNotes = "",
                                            defaultPaymentTerms = "",
                                            defaultValidityDays = 15
                                        )
                                    )
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
