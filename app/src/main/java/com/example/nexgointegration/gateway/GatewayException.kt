package com.example.nexgointegration.gateway

class GatewayException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)