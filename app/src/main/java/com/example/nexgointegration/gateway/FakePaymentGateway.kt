package com.example.nexgointegration.gateway

import kotlinx.coroutines.delay

class FakePaymentGateway : PaymentGateway {

    override suspend fun pay(
        request: PaymentGatewayRequest
    ): PaymentGatewayResponsePayment {

        println("========================================")
        println("FAKE GATEWAY")
        println("Transaction : ${request.transactionId}")
        println("Amount      : ${request.amount}")
        println("Currency    : ${request.currency}")
        println("Terminal    : ${request.terminalId}")
        println("Merchant    : ${request.merchantId}")
        println("Entry Mode  : ${request.entryMode}")
        println("========================================")

        // Simule le temps de communication avec le serveur
        delay(1000)

        /*
         * TEST MODE
         *
         * 10000 -> APPROVED
         * 50000 -> DECLINED
         * 99999 -> NETWORK ERROR
         */

        return when (request.amount) {

            "10000" -> {

                PaymentGatewayResponsePayment(
                    success = true,
                    responseCode = "00",
                    message = "APPROVED",
                    transactionId = request.transactionId,
                    authorizationCode = "123456",
                    gatewayTransactionId = "FAKE-${request.transactionId}"
                )
            }

            "50000" -> {

                PaymentGatewayResponsePayment(
                    success = false,
                    responseCode = "05",
                    message = "DECLINED",
                    transactionId = request.transactionId,
                    authorizationCode = null,
                    gatewayTransactionId = "FAKE-${request.transactionId}"
                )
            }

            "99999" -> {

                throw RuntimeException(
                    "FAKE NETWORK ERROR"
                )
            }

            else -> {

                PaymentGatewayResponsePayment(
                    success = true,
                    responseCode = "00",
                    message = "APPROVED",
                    transactionId = request.transactionId,
                    authorizationCode = "FAKE001",
                    gatewayTransactionId = "FAKE-${request.transactionId}"
                )
            }
        }
    }

    override suspend fun cancel(
        transactionId: String
    ): PaymentGatewayResponsePayment {

        return PaymentGatewayResponsePayment(
            success = true,
            responseCode = "00",
            message = "CANCELLED",
            transactionId = transactionId,
            authorizationCode = null,
            gatewayTransactionId = "FAKE-CANCEL-$transactionId"
        )
    }

    override suspend fun checkStatus(
        transactionId: String
    ): PaymentGatewayResponsePayment {

        return PaymentGatewayResponsePayment(
            success = true,
            responseCode = "00",
            message = "TRANSACTION FOUND",
            transactionId = transactionId,
            authorizationCode = "123456",
            gatewayTransactionId = "FAKE-$transactionId"
        )
    }
}