package com.example.terminalmodule.model

data class EmvTerminalParam(
    val contactCapabilities: String,
    val contactlessCapabilities: String,
    val contactlessTtq: String
)