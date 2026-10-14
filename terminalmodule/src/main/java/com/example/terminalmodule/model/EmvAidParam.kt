package com.example.terminalmodule.model

data class EmvAidParam(
    val aid: String,
    val asi: Int = 0,
    val appVersion: String,
    val tacDenial: String,
    val tacOnline: String,
    val tacDefault: String,
    val floorLimit: Long,
    val threshold: Long = 0L,
    val contactlessFloorLimit: Long,
    val contactlessCvmLimit: Long,
    val contactlessTransLimit: Long
)