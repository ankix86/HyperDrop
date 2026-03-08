package com.lantransfer.app.crypto

import com.lantransfer.app.util.toByteArray
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.interfaces.XECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoEngine {
    data class KeyPairData(val privateEncoded: ByteArray, val publicEncoded: ByteArray)

    companion object {
        // SubjectPublicKeyInfo prefix for X25519 keys.
        private val X25519_SPKI_PREFIX = byteArrayOf(
            0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x6E, 0x03, 0x21, 0x00
        )
    }

    fun generateX25519KeyPair(): KeyPairData {
        val kpg = KeyPairGenerator.getInstance("X25519")
        val kp = kpg.generateKeyPair()
        val pub = kp.public
        val rawPublic = when (pub) {
            is XECPublicKey -> {
                val u = pub.u.toByteArray()
                // BigInteger may include a sign byte or be shorter than 32 bytes.
                if (u.size == 32) {
                    u
                } else if (u.size > 32) {
                    u.copyOfRange(u.size - 32, u.size)
                } else {
                    ByteArray(32 - u.size) + u
                }
            }
            else -> {
                // Fallback: strip SPKI prefix if present.
                val encoded = pub.encoded
                if (encoded.size >= X25519_SPKI_PREFIX.size + 32 && encoded.copyOfRange(0, X25519_SPKI_PREFIX.size).contentEquals(X25519_SPKI_PREFIX)) {
                    encoded.copyOfRange(X25519_SPKI_PREFIX.size, X25519_SPKI_PREFIX.size + 32)
                } else {
                    encoded
                }
            }
        }

        return KeyPairData(kp.private.encoded, rawPublic)
    }

    fun deriveSharedSecret(privateEncoded: ByteArray, peerPublicEncoded: ByteArray): ByteArray {
        val factory = KeyFactory.getInstance("X25519")
        val privateKey = factory.generatePrivate(PKCS8EncodedKeySpec(privateEncoded))
        val peerPublicX509 = if (peerPublicEncoded.size == 32) {
            X25519_SPKI_PREFIX + peerPublicEncoded
        } else {
            peerPublicEncoded
        }
        val peerPublic: PublicKey = factory.generatePublic(X509EncodedKeySpec(peerPublicX509))
        val ka = KeyAgreement.getInstance("X25519")
        ka.init(privateKey)
        ka.doPhase(peerPublic, true)
        return ka.generateSecret()
    }

    fun deriveSessionKey(sharedSecret: ByteArray, transcript: ByteArray): ByteArray {
        return hkdfSha256(sharedSecret, "lan-transfer-session-key".toByteArray() + transcript, 32)
    }

    fun encryptChunk(key: ByteArray, salt8: ByteArray, index: Int, aad: ByteArray, plain: ByteArray): ByteArray {
        val nonce = salt8 + index.toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(plain)
    }

    fun decryptChunk(key: ByteArray, salt8: ByteArray, index: Int, aad: ByteArray, encrypted: ByteArray): ByteArray {
        val nonce = salt8 + index.toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(encrypted)
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun hkdfSha256(ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = hmacSha256(ByteArray(32) { 0 }, ikm)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))

        var t = ByteArray(0)
        val out = ArrayList<Byte>()
        var i = 1
        while (out.size < length) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()
            t.forEach { out.add(it) }
            i++
        }
        return out.take(length).toByteArray()
    }

    private fun hmacSha256(key: ByteArray, input: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(input)
    }
}
