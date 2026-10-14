package com.example.nexgointegration.parameters

fun cleanHex(s: String): String =
    s.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()