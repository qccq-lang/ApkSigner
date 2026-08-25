package com.example.logic

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class KeystoreDetails(
    val file: File,
    val aliases: List<String>,
    val firstAlias: String?,
    val subject: String,
    val issuer: String,
    val validFrom: String,
    val validUntil: String,
    val isExpired: Boolean,
    val algorithm: String,
    val keySize: String,
    val md5Fingerprint: String,
    val sha1Fingerprint: String,
    val sha256Fingerprint: String
)

object KeystoreManager {

    init {
        // Register Bouncy Castle provider
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    /**
     * Generate a new Keystore with custom parameters
     */
    fun generateKeystore(
        file: File,
        alias: String,
        keystorePass: String,
        keyPass: String,
        commonName: String,
        orgUnit: String = "",
        orgName: String = "",
        locality: String = "",
        state: String = "",
        country: String = "",
        validityYears: Int = 25,
        algorithm: String = "RSA",
        keySize: Int = 2048
    ): Result<Unit> {
        return try {
            val bcProvider = BouncyCastleProvider()
            val keyPair = if (algorithm.equals("EC", ignoreCase = true)) {
                val kpg = KeyPairGenerator.getInstance("EC", bcProvider)
                kpg.initialize(ECGenParameterSpec("secp256r1"))
                kpg.generateKeyPair()
            } else {
                val kpg = KeyPairGenerator.getInstance("RSA")
                kpg.initialize(keySize)
                kpg.generateKeyPair()
            }

            val dnameBuilder = X500NameBuilder(BCStyle.INSTANCE)
            if (commonName.isNotBlank()) dnameBuilder.addRDN(BCStyle.CN, commonName)
            if (orgUnit.isNotBlank()) dnameBuilder.addRDN(BCStyle.OU, orgUnit)
            if (orgName.isNotBlank()) dnameBuilder.addRDN(BCStyle.O, orgName)
            if (locality.isNotBlank()) dnameBuilder.addRDN(BCStyle.L, locality)
            if (state.isNotBlank()) dnameBuilder.addRDN(BCStyle.ST, state)
            if (country.isNotBlank()) dnameBuilder.addRDN(BCStyle.C, country.take(2).uppercase())

            val x500Name = dnameBuilder.build()

            val calendar = Calendar.getInstance()
            val notBefore = calendar.time
            calendar.add(Calendar.YEAR, validityYears)
            val notAfter = calendar.time

            val serialNumber = BigInteger(64, java.security.SecureRandom())

            val certBuilder = JcaX509v3CertificateBuilder(
                x500Name,
                serialNumber,
                notBefore,
                notAfter,
                x500Name,
                keyPair.public
            )

            val signatureAlgorithm = if (algorithm.equals("EC", ignoreCase = true)) {
                "SHA256withECDSA"
            } else {
                "SHA256WithRSAEncryption"
            }

            val signer = JcaContentSignerBuilder(signatureAlgorithm)
                .setProvider(bcProvider)
                .build(keyPair.private)

            val cert = JcaX509CertificateConverter()
                .setProvider(bcProvider)
                .getCertificate(certBuilder.build(signer))

            val keyStore = KeyStore.getInstance("PKCS12")
            keyStore.load(null, null)
            keyStore.setKeyEntry(alias, keyPair.private, keyPass.toCharArray(), arrayOf(cert))

            file.parentFile?.mkdirs()
            FileOutputStream(file).use {
                keyStore.store(it, keystorePass.toCharArray())
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get or create a standard built-in TestKey (AOSP standard debug key)
     */
    fun getOrCreateTestKey(context: Context): File {
        val internalKey = File(context.filesDir, "android_debug_testkey.jks")
        val dirKey = File(AppStorageManager.getKeystoresDir(context), "android_debug_testkey.jks")

        // 1. If it already exists in keystores dir and is valid (> 0 bytes), use it
        if (dirKey.exists() && dirKey.length() > 0) {
            return dirKey
        }

        // 2. If it already exists in internal files dir and is valid, use it
        if (internalKey.exists() && internalKey.length() > 0) {
            return internalKey
        }

        // 3. Try to generate into the keystores directory
        val result = generateKeystore(
            file = dirKey,
            alias = "androiddebugkey",
            keystorePass = "android",
            keyPass = "android",
            commonName = "Android Debug",
            orgName = "Android",
            country = "US",
            validityYears = 30
        )

        if (result.isSuccess && dirKey.exists() && dirKey.length() > 0) {
            return dirKey
        }

        // 4. Fallback to generating in internal app storage (guaranteed writable)
        val fallbackResult = generateKeystore(
            file = internalKey,
            alias = "androiddebugkey",
            keystorePass = "android",
            keyPass = "android",
            commonName = "Android Debug",
            orgName = "Android",
            country = "US",
            validityYears = 30
        )

        if (fallbackResult.isSuccess && internalKey.exists() && internalKey.length() > 0) {
            return internalKey
        }

        throw Exception("Gagal membuat TestKey bawaan: ${fallbackResult.exceptionOrNull()?.message ?: result.exceptionOrNull()?.message}")
    }

    /**
     * Inspect a Keystore file and extract certificate info & fingerprints
     */
    fun inspectKeystore(file: File, password: String): Result<KeystoreDetails> {
        return try {
            val keyStore = loadKeyStore(file, password)
            val aliases = keyStore.aliases().toList()
            if (aliases.isEmpty()) {
                return Result.failure(Exception("Keystore tidak memiliki alias atau sertifikat."))
            }

            val firstAlias = aliases.first()
            val cert = keyStore.getCertificate(firstAlias) as? X509Certificate
                ?: return Result.failure(Exception("Tidak dapat membaca sertifikat untuk alias: $firstAlias"))

            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            val validFrom = dateFormat.format(cert.notBefore)
            val validUntil = dateFormat.format(cert.notAfter)
            val isExpired = Date().after(cert.notAfter)

            val md5 = formatFingerprint(MessageDigest.getInstance("MD5").digest(cert.encoded))
            val sha1 = formatFingerprint(MessageDigest.getInstance("SHA-1").digest(cert.encoded))
            val sha256 = formatFingerprint(MessageDigest.getInstance("SHA-256").digest(cert.encoded))

            val algorithm = cert.sigAlgName ?: cert.publicKey.algorithm
            val keySize = when {
                cert.publicKey.algorithm == "RSA" -> {
                    try {
                        val rsaKey = cert.publicKey as java.security.interfaces.RSAPublicKey
                        "${rsaKey.modulus.bitLength()} bits"
                    } catch (_: Exception) { "2048 bits" }
                }
                cert.publicKey.algorithm == "EC" -> "256 bits (EC)"
                else -> cert.publicKey.algorithm
            }

            Result.success(
                KeystoreDetails(
                    file = file,
                    aliases = aliases,
                    firstAlias = firstAlias,
                    subject = cert.subjectX500Principal.name,
                    issuer = cert.issuerX500Principal.name,
                    validFrom = validFrom,
                    validUntil = validUntil,
                    isExpired = isExpired,
                    algorithm = algorithm,
                    keySize = keySize,
                    md5Fingerprint = md5,
                    sha1Fingerprint = sha1,
                    sha256Fingerprint = sha256
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Load KeyStore trying PKCS12 and fallback to BKS or JKS
     */
    fun loadKeyStore(file: File, password: String): KeyStore {
        val types = listOf("PKCS12", "JKS", "BKS")
        var lastException: Exception? = null

        for (type in types) {
            try {
                val ks = KeyStore.getInstance(type)
                FileInputStream(file).use { fis ->
                    ks.load(fis, password.toCharArray())
                }
                return ks
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw lastException ?: Exception("Gagal memuat Keystore. Periksa password.")
    }

    private fun formatFingerprint(bytes: ByteArray): String {
        return bytes.joinToString(":") { "%02X".format(it) }
    }

    fun listKeystores(context: Context): List<File> {
        val result = mutableListOf<File>()
        try {
            val dir = AppStorageManager.getKeystoresDir(context)
            dir.listFiles { file -> file.isFile && (file.extension in listOf("jks", "keystore", "p12", "bks") || file.name.contains("key")) }
                ?.let { result.addAll(it) }
        } catch (_: Exception) {}

        try {
            val internalKey = File(context.filesDir, "android_debug_testkey.jks")
            if (internalKey.exists() && internalKey.length() > 0 && result.none { it.name == internalKey.name }) {
                result.add(internalKey)
            }
        } catch (_: Exception) {}

        return result.sortedByDescending { it.lastModified() }
    }
}
