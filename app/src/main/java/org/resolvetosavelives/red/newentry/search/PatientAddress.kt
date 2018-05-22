package org.resolvetosavelives.red.newentry.search

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import org.threeten.bp.Instant

@Entity
data class PatientAddress(
    @PrimaryKey
    val uuid: String,

    val colonyOrVillage: String?,

    val district: String?,

    val state: String?,

    val country: String?,

    val createdAt: Instant,

    val updatedAt: Instant
)
