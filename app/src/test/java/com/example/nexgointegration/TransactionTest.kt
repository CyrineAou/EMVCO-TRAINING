import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionTest {

    @Test
    fun test_meme_pays_doit_etre_domestic() {
        // Test : Même code pays (ex: "0250" pour la France)
        val resultat = isDomesticTransaction("0250", "0250")
        assertTrue("Ça devrait être true (même pays)", resultat)
    }

    @Test
    fun test_pays_differents_doit_etre_international() {
        // Test : Pays différents (France vs USA)
        val resultat = isDomesticTransaction("0250", "0840")
        assertFalse("Ça devrait être false (pays différents)", resultat)
    }

    @Test
    fun test_code_manquant_doit_etre_international() {
        // Test : Un des codes est absent (null)
        val resultat = isDomesticTransaction("0250", null)
        assertFalse("Ça devrait être false s'il manque un code", resultat)
    }

    fun isDomesticTransaction(issuerCountryCode: String?, terminalCountryCode: String?): Boolean {
        return issuerCountryCode != null && terminalCountryCode != null && issuerCountryCode == terminalCountryCode
    }
}