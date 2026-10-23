package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

@Database(
    entities = [
        ProdukProfilEntity::class,
        TransaksiEntity::class,
        TransaksiDetailEntity::class,
        DraftAssemblyEntity::class,
        Product::class,
        InventoryItem::class,
        Transaction::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(AppTypeConverters::class)
abstract class TokoYogaDatabase : RoomDatabase() {

    abstract fun tokoYogaDao(): TokoYogaDao
    abstract fun productDao(): ProductDao
    abstract fun inventoryItemDao(): InventoryItemDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: TokoYogaDatabase? = null

        fun getInstance(context: Context): TokoYogaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TokoYogaDatabase::class.java,
                    "tokoyoga_offline.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Inisialisasi data awal profil ekstrusi aluminium standar toko
                            CoroutineScope(Dispatchers.IO).launch {
                                val database = getInstance(context)
                                database.tokoYogaDao().insertAllProduk(initialProfiles)

                                // Inisialisasi data awal Product & InventoryItem
                                val productIds = database.productDao().insertAllProducts(initialProductEntities)
                                val initialInventory = productIds.mapIndexed { index, id ->
                                    val prod = initialProductEntities[index]
                                    InventoryItem(
                                        productId = id.toInt(),
                                        sectionNumber = prod.sectionNumber,
                                        batchNumber = "BATCH-2026-${100 + index}",
                                        quantityStock = 25 + index * 5,
                                        standardLengthMm = 6000.0,
                                        minStockAlert = 5,
                                        warehouseLocation = "Rak Profil ${('A' + index)}",
                                        status = InventoryStatus.IN_STOCK,
                                        lastRestocked = Date()
                                    )
                                }
                                database.inventoryItemDao().insertAllInventoryItems(initialInventory)
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        val initialProfiles = listOf(
            ProdukProfilEntity(
                sectionNo = "SF-001 Open Back",
                kategori = "Shop Front",
                thickness = 1.25,
                hargaPerMeter = 48000,
                warna = "Silver",
                file3dPath = ""
            ),
            ProdukProfilEntity(
                sectionNo = "SD-9055 Kusen Pintu Geser",
                kategori = "Sliding Door",
                thickness = 1.35,
                hargaPerMeter = 62000,
                warna = "Black",
                file3dPath = ""
            ),
            ProdukProfilEntity(
                sectionNo = "CW-201 Kusen Jendela Jungkit",
                kategori = "Casement Window",
                thickness = 1.15,
                hargaPerMeter = 42000,
                warna = "Brown",
                file3dPath = ""
            ),
            ProdukProfilEntity(
                sectionNo = "CT-500 Mullion Curtain Wall",
                kategori = "Curtain Wall",
                thickness = 2.00,
                hargaPerMeter = 125000,
                warna = "Silver",
                file3dPath = ""
            ),
            ProdukProfilEntity(
                sectionNo = "Pipa Hollow Kotak 40x40",
                kategori = "Commodities",
                thickness = 1.20,
                hargaPerMeter = 38000,
                warna = "Putih",
                file3dPath = ""
            ),
            ProdukProfilEntity(
                sectionNo = "Pipa Hollow Persegi 50x100",
                kategori = "Commodities",
                thickness = 1.50,
                hargaPerMeter = 85000,
                warna = "Silver",
                file3dPath = ""
            )
        )

        val initialProductEntities = listOf(
            Product(
                sectionNumber = "SF-001 Open Back",
                name = "Kusen Shop Front Open Back 4 Inch",
                category = ProductCategory.SHOP_FRONT,
                thickness = 1.25,
                unitPrice = 48000L,
                color = "Silver",
                description = "Profil aluminium kusen shop front standar retail"
            ),
            Product(
                sectionNumber = "SD-9055 Kusen Pintu Geser",
                name = "Profil Daun & Rel Pintu Geser (Sliding Door)",
                category = ProductCategory.SLIDING_DOOR,
                thickness = 1.35,
                unitPrice = 62000L,
                color = "Black",
                description = "Profil aluminium pintu geser kedap suara & angin"
            ),
            Product(
                sectionNumber = "CW-201 Kusen Jendela Jungkit",
                name = "Kusen Jendela Jungkit / Casement Window",
                category = ProductCategory.CASEMENT_WINDOW,
                thickness = 1.15,
                unitPrice = 42000L,
                color = "Brown",
                description = "Profil jendela casement finishing anodized brown"
            ),
            Product(
                sectionNumber = "CT-500 Mullion Curtain Wall",
                name = "Mullion Struktural Curtain Wall Gedung",
                category = ProductCategory.CURTAIN_WALL,
                thickness = 2.00,
                unitPrice = 125000L,
                color = "Silver",
                description = "Profil aluminium heavy-duty fasad kaca gedung bertingkat"
            ),
            Product(
                sectionNumber = "Pipa Hollow Kotak 40x40",
                name = "Hollow Persegi Ekstrusi 40 x 40 mm",
                category = ProductCategory.COMMODITIES,
                thickness = 1.20,
                unitPrice = 38000L,
                color = "Putih",
                description = "Hollow aluminium serbaguna untuk partisi & rangka"
            ),
            Product(
                sectionNumber = "Pipa Hollow Persegi 50x100",
                name = "Hollow Persegi Panjang Ekstrusi 50 x 100 mm",
                category = ProductCategory.COMMODITIES,
                thickness = 1.50,
                unitPrice = 85000L,
                color = "Silver",
                description = "Hollow balok aluminium struktural bentang lebar"
            )
        )
    }
}
