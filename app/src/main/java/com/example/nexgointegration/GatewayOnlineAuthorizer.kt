package com.example.nexgointegration


import android.util.Log
import com.example.nexgointegration.gateway.PaymentGateway
import com.example.terminalmodule.api.EmvOnlineAuthorizer
import com.example.terminalmodule.model.EmvOnlineRequest
import com.example.terminalmodule.model.EmvOnlineResponse

class GatewayOnlineAuthorizer(
    private val gateway: PaymentGateway
) : EmvOnlineAuthorizer {

    override fun authorize(request: EmvOnlineRequest): EmvOnlineResponse {
        Log.d("EMV_ONLINE", "Autorisation demandée, ${request.tags.size} tags : ${request.tags}")

        // TODO : envoyer request.tags (ARQC 9F26, 9F10, 9F37, 9F36, 95, 9A, 9C, 9F02, 5F2A...)
        // au gateway Worldline, puis convertir sa réponse en EmvOnlineResponse.
        // SIMULATION (comme ton ancien test) : à remplacer.
        return EmvOnlineResponse(approved = true, authCode = "AUTH88")
    }
}