package com.sndiy.chatfin.core.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class TransactionWithItems(
    @Embedded
    val transaction: TransactionEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "transactionId"
    )
    val items: List<TransactionItemEntity> = emptyList()
)
