package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "transactions",
    indices = [
        Index("transaction_code"),
        Index("timestamp")
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "transaction_code")
    val transactionCode: String,

    @ColumnInfo(name = "type")
    val type: TransactionType = TransactionType.SALE,

    @ColumnInfo(name = "total_amount")
    val totalAmount: Long = 0L,

    @ColumnInfo(name = "payment_method")
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,

    @ColumnInfo(name = "customer_name")
    val customerName: String = "",

    @ColumnInfo(name = "notes")
    val notes: String = "",

    @ColumnInfo(name = "status")
    val status: TransactionStatus = TransactionStatus.COMPLETED,

    @ColumnInfo(name = "timestamp")
    val timestamp: Date = Date()
)

typealias TransactionRecord = Transaction
