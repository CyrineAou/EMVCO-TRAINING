package com.example.terminalmodule.model


data class EmvCapkParam(
    val rid: String,
    val index: Int,
    val hashInd: Int,
    val arithInd: Int,
    val modulus: String,
    val exponent: String,
    val expireDate: String,   // YYMMDD
    val checkSum: String
)