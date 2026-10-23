package com.example.data.local

import androidx.room.TypeConverter
import java.util.Date

enum class ProductCategory(val displayName: String) {
    SHOP_FRONT("Shop Front"),
    SLIDING_DOOR("Sliding Door"),
    CASEMENT_WINDOW("Casement Window"),
    CURTAIN_WALL("Curtain Wall"),
    COMMODITIES("Commodities"),
    ACCESSORIES("Accessories"),
    CUSTOM("Custom Profile");

    companion object {
        fun fromString(value: String?): ProductCategory {
            if (value.isNullOrBlank()) return COMMODITIES
            return entries.firstOrNull { 
                it.name.equals(value, ignoreCase = true) || 
                it.displayName.equals(value, ignoreCase = true) 
            } ?: COMMODITIES
        }
    }
}

enum class InventoryStatus(val displayName: String) {
    IN_STOCK("In Stock"),
    LOW_STOCK("Low Stock"),
    OUT_OF_STOCK("Out of Stock"),
    RESERVED("Reserved");

    companion object {
        fun fromString(value: String?): InventoryStatus {
            if (value.isNullOrBlank()) return IN_STOCK
            return entries.firstOrNull { 
                it.name.equals(value, ignoreCase = true) || 
                it.displayName.equals(value, ignoreCase = true) 
            } ?: IN_STOCK
        }
    }
}

enum class TransactionType(val displayName: String) {
    SALE("Penjualan POS"),
    RESTOCK("Penerimaan Stok / Restock"),
    RETURN("Pengembalian / Retur"),
    ADJUSTMENT("Penyesuaian Stok / Opname");

    companion object {
        fun fromString(value: String?): TransactionType {
            if (value.isNullOrBlank()) return SALE
            return entries.firstOrNull { 
                it.name.equals(value, ignoreCase = true) || 
                it.displayName.equals(value, ignoreCase = true) 
            } ?: SALE
        }
    }
}

enum class PaymentMethod(val displayName: String) {
    CASH("Tunai / Cash"),
    TRANSFER("Transfer Bank"),
    QRIS("QRIS"),
    TEMPO("Tempo / Hutang");

    companion object {
        fun fromString(value: String?): PaymentMethod {
            if (value.isNullOrBlank()) return CASH
            return entries.firstOrNull { 
                it.name.equals(value, ignoreCase = true) || 
                it.displayName.equals(value, ignoreCase = true) 
            } ?: CASH
        }
    }
}

enum class TransactionStatus(val displayName: String) {
    COMPLETED("Selesai / Lunas"),
    PENDING("Menunggu Pembayaran"),
    CANCELLED("Dibatalkan");

    companion object {
        fun fromString(value: String?): TransactionStatus {
            if (value.isNullOrBlank()) return COMPLETED
            return entries.firstOrNull { 
                it.name.equals(value, ignoreCase = true) || 
                it.displayName.equals(value, ignoreCase = true) 
            } ?: COMPLETED
        }
    }
}

class AppTypeConverters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromProductCategory(category: ProductCategory?): String? {
        return category?.name
    }

    @TypeConverter
    fun toProductCategory(value: String?): ProductCategory? {
        return value?.let { ProductCategory.fromString(it) }
    }

    @TypeConverter
    fun fromInventoryStatus(status: InventoryStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toInventoryStatus(value: String?): InventoryStatus? {
        return value?.let { InventoryStatus.fromString(it) }
    }

    @TypeConverter
    fun fromTransactionType(type: TransactionType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toTransactionType(value: String?): TransactionType? {
        return value?.let { TransactionType.fromString(it) }
    }

    @TypeConverter
    fun fromPaymentMethod(method: PaymentMethod?): String? {
        return method?.name
    }

    @TypeConverter
    fun toPaymentMethod(value: String?): PaymentMethod? {
        return value?.let { PaymentMethod.fromString(it) }
    }

    @TypeConverter
    fun fromTransactionStatus(status: TransactionStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toTransactionStatus(value: String?): TransactionStatus? {
        return value?.let { TransactionStatus.fromString(it) }
    }

    @TypeConverter
    fun fromStringList(strings: List<String>?): String? {
        return strings?.joinToString(separator = "|||")
    }

    @TypeConverter
    fun toStringList(data: String?): List<String>? {
        return if (data.isNullOrEmpty()) {
            emptyList()
        } else {
            data.split("|||")
        }
    }
}
