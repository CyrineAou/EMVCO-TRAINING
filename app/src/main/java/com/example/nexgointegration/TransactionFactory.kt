package com.example.nexgointegration

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object TransactionFactory {

    fun createId(): String {
        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .uppercase()
    }

    fun timestamp(): String {
        return SimpleDateFormat(
            "yyyyMMddHHmmss",
            Locale.US
        ).format(Date())
    }
}