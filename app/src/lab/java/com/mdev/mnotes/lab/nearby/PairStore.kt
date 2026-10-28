package com.mdev.mnotes.lab.nearby

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Klucz pary z drugim urządzeniem, zapisany zaszyfrowany kluczem z Android Keystore (nie jawnie w preferencjach),
 * bez dodatkowej biblioteki. Jedna para naraz: telefon ↔ tablet.
 */
class PairStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("mnotes.lab.pair", Context.MODE_PRIVATE)

    val paired: Boolean get() = sp.contains(K_BLOB)
    val peerName: String get() = sp.getString(K_NAME, "").orEmpty()

    /** Adres Bluetooth drugiego urządzenia (2e-B1): telefon łączy się z nim wprost, bez szukania; pusty = nieznany. */
    var peerAddress: String
        get() = sp.getString(K_MAC, "").orEmpty()
        set(v) { if (v != peerAddress) sp.edit().putString(K_MAC, v).apply() }

    /** Zapisuje klucz pary; false, gdy Keystore odmówił (wtedy nic nie zapisano). [address]: null przy parze przez Nearby. */
    fun save(pairKey: ByteArray, peer: String, address: String? = null): Boolean = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
        val blob = c.iv + c.doFinal(pairKey)
        sp.edit().putString(K_BLOB, Base64.encodeToString(blob, Base64.NO_WRAP)).putString(K_NAME, peer).putString(K_MAC, address.orEmpty()).apply()
    }.isSuccess

    fun load(): ByteArray? = runCatching {
        val blob = Base64.decode(sp.getString(K_BLOB, null) ?: return null, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, blob, 0, 12)) }
        c.doFinal(blob, 12, blob.size - 12)
    }.getOrNull()

    /**
     * Klucz pary do połączenia. Zapisany, ale nie do odczytania (Keystore zgubił klucz, np. po przywróceniu kopii
     * zapasowej na nowe urządzenie) → para usunięta i null: okna mówią „Para wygasła” zamiast czekać w nieskończoność.
     */
    fun loadOrForget(): ByteArray? = load() ?: run { if (paired) clear(); null }

    fun clear() { sp.edit().clear().apply() }

    private fun wrappingKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val ALIAS = "mnotes-nearby-pair"
        const val K_BLOB = "pair.blob"
        const val K_NAME = "pair.name"
        const val K_MAC = "pair.mac"
    }
}
