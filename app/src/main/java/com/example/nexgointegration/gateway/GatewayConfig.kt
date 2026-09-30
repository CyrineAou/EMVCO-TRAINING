package com.example.nexgointegration.gateway


object GatewayConfig {

    /*
     * À remplacer par l'adresse réelle de ta passerelle.
     */
    const val BASE_URL = "https://YOUR-GATEWAY-HOST"

    const val PAYMENT_ENDPOINT = "/payment"
    const val CANCEL_ENDPOINT = "/payment/cancel"
    const val STATUS_ENDPOINT = "/payment/status"

    const val CONNECT_TIMEOUT_SECONDS = 15L
    const val READ_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 30L
}