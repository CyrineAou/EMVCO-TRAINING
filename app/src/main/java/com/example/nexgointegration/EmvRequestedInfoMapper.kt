package com.example.nexgointegration

import com.example.terminalmodule.model.EmvAidParam
import com.example.terminalmodule.model.EmvPaymentResult
import com.magellan.tapandgo.validatorsdk.model.emvdata.EmvRequestedInfoGroup


/**
 * Construit EmvRequestedInfoGroup uniquement à partir des tags EMV du résultat.
 * L'ordre des arguments suit EXACTEMENT le constructeur du SDK (40 paramètres).
 * Une valeur absente est transmise à null (jamais inventée).
 */
object EmvRequestedInfoMapper {

    fun build(result: EmvPaymentResult, aids: List<EmvAidParam>): EmvRequestedInfoGroup? {
        val tags = result.tags

        fun tag(name: String): String? =
            tags.entries
                .firstOrNull { it.key.equals(name, ignoreCase = true) }
                ?.value
                ?.takeIf { it.isNotBlank() }

        // TAC : ce ne sont pas des tags carte, ils viennent de la config de l'AID sélectionné
        val selectedAid = (tag("4F") ?: tag("84"))?.uppercase()
        val aidParam = selectedAid?.let { sel ->
            aids.firstOrNull { sel.startsWith(it.aid.uppercase()) }
        }

        return tag("9A")?.let {
            EmvRequestedInfoGroup(
                it,                       // 1  transactionDate
                tag("9F21") ?: "",                     // 2  transactionTime
                tag("56") ?: "",                       // 3  track1
                tag("57") ?: "",                       // 4  track2
                tag("9F15") ?: "",                     // 5  merchantCategoryCode
                tag("5F34"),                     // 6  cardSequenceNumber
                tag("9F03") ?: "",                     // 7  otherAmount
                tag("9F33") ?: "",                     // 8  terminalCapabilities
                tag("9F02") ?: "",                     // 9  amountAuthorized
                tag("9F1A") ?: "",                     // 10 terminalCountryCode
                tag("9F6E") ?: "",                     // 11 terminalFormFactor (à vérifier)
                tag("5F24") ?: "",                     // 12 cardExpirationDate
                tag("82") ?: "",                       // 13 AIP
                tag("95"),                       // 14 TVR
                null ?: "",                            // 15 RTT (tag inconnu : à confirmer)
                tag("9F10") ?: "",                     // 16 IAD
                tag("9F26") ?: "",                     // 17 AC
                tag("9F27") ?: "",                     // 18 CID
                tag("9F36") ?: "",                     // 19 ATC
                tag("9F37") ?: "",                     // 20 UN
                tag("9F35") ?: "",                     // 21 EMV_TERM_TYPE
                tag("9F08") ?: tag("9F09"),      // 22 APPL_VER_NUM
                tag("84") ?: tag("4F") ?: "",          // 23 DFName
                tag("9F1E") ?: "",                     // 24 TERM_SER_NUM
                tag("9F34"),                     // 25 CVMResults
                tag("9F1C") ?: "",                     // 26 TIDTerminal
                tag("9F66"),                     // 27 TTQ
                null,                            // 28 CED (tag inconnu : à confirmer)
                tag("9F6E"),                     // 29 formFactorIndicator
                result.code,                     // 30 microcircuitProcessingResult (à confirmer)
                tag("9F07"),                     // 31 applicationUsageControl
                tag("9F0D"),                     // 32 issuerActionCodeDefault
                tag("9F0E"),                     // 33 issuerActionCodeDenial
                tag("9F0F"),                     // 34 issuerActionCodeOnline
                aidParam?.tacDefault,            // 35 terminalActionCodeDefault
                aidParam?.tacDenial,             // 36 terminalActionCodeDenial
                aidParam?.tacOnline,             // 37 terminalActionCodeOnline
                tag("8E"),                       // 38 CVMList
                tag("5F25"),                     // 39 effectiveDate
                tag("5F28")                      // 40 issuerCountryCode
            )
        }
    }
}