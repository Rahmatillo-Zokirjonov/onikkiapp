package com.onikki.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SmsImportStatus { IMPORTED, UNRECOGNIZED, DISMISSED }

/**
 * Every bank SMS the app looked at. [hash] (sender + body + minute) makes re-delivery or a re-scan
 * idempotent; UNRECOGNIZED rows are kept so the user can see what the parser missed and add it by hand.
 */
@Entity(tableName = "sms_imports")
data class SmsImport(
    @PrimaryKey val hash: String,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val status: SmsImportStatus,
    val transactionId: Long? = null
)

/** Learned: this shop's purchases go to this category (set whenever the user re-categorises one). */
@Entity(tableName = "merchant_categories")
data class MerchantCategory(
    @PrimaryKey val merchant: String,
    val category: String
)
