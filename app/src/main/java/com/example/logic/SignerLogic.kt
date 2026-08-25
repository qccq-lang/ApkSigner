package com.example.logic

import android.content.Context
import android.net.Uri
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipFile

data class SignerCertInfo(
    val subject: String,
    val issuer: String,
    val validFrom: String,
    val validUntil: String,
    val sha256Fingerprint: String,
    val sha1Fingerprint: String,
    val md5Fingerprint: String
)

data class ApkVerificationDetails(
    val isVerified: Boolean,
    val isV1Scheme: Boolean,
    val isV2Scheme: Boolean,
    val isV3Scheme: Boolean,
    val isV4Scheme: Boolean,
    val signers: List<SignerCertInfo>,
    val errors: List<String>,
    val warnings: List<String>,
    val rawSummary: String
)

object SignerLogic {

    suspend fun signApk(
        inputApk: File,
        outputApk: File,
        keystoreFile: File,
        keystorePass: String,
        keyAlias: String,
        keyPass: String,
        enableV1: Boolean = true,
        enableV2: Boolean = true,
        enableV3: Boolean = true,
        minSdkVersion: Int = 24
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!inputApk.exists()) {
                return@withContext Result.failure(Exception("File APK sumber tidak ditemukan: ${inputApk.name}"))
            }

            val ks = KeystoreManager.loadKeyStore(keystoreFile, keystorePass)
            val key = ks.getKey(keyAlias, keyPass.toCharArray())
                ?: return@withContext Result.failure(Exception("Kunci dengan alias '$keyAlias' tidak ditemukan dalam Keystore."))

            val privateKey = key as PrivateKey
            val certChain = ks.getCertificateChain(keyAlias)?.map { it as X509Certificate }?.toList()
                ?: return@withContext Result.failure(Exception("Rantai sertifikat untuk alias '$keyAlias' kosong."))

            val signerConfig = ApkSigner.SignerConfig.Builder("signer", privateKey, certChain).build()

            outputApk.parentFile?.mkdirs()

            val builder = ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(inputApk)
                .setOutputApk(outputApk)
                .setMinSdkVersion(minSdkVersion)
                .setV1SigningEnabled(enableV1)
                .setV2SigningEnabled(enableV2)
                .setV3SigningEnabled(enableV3)

            val apkSigner = builder.build()
            apkSigner.sign()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyApkDetailed(apk: File): ApkVerificationDetails = withContext(Dispatchers.IO) {
        try {
            val verifier = ApkVerifier.Builder(apk).build()
            val result = verifier.verify()

            val signersList = mutableListOf<SignerCertInfo>()
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

            val certs = try {
                result.signerCertificates
            } catch (_: Exception) {
                emptyList<X509Certificate>()
            }

            for (cert in certs) {
                val md5 = formatFingerprint(MessageDigest.getInstance("MD5").digest(cert.encoded))
                val sha1 = formatFingerprint(MessageDigest.getInstance("SHA-1").digest(cert.encoded))
                val sha256 = formatFingerprint(MessageDigest.getInstance("SHA-256").digest(cert.encoded))

                signersList.add(
                    SignerCertInfo(
                        subject = cert.subjectX500Principal.name,
                        issuer = cert.issuerX500Principal.name,
                        validFrom = dateFormat.format(cert.notBefore),
                        validUntil = dateFormat.format(cert.notAfter),
                        sha256Fingerprint = sha256,
                        sha1Fingerprint = sha1,
                        md5Fingerprint = md5
                    )
                )
            }

            val errors = result.errors.map { it.toString() }
            val warnings = result.warnings.map { it.toString() }

            val rawSummary = buildString {
                if (result.isVerified) {
                    appendLine("Status: TERVERIFIKASI RESMI")
                    appendLine("• v1 JAR Scheme: ${if (result.isVerifiedUsingV1Scheme) "Aktif" else "Tidak"}")
                    appendLine("• v2 APK Signature Scheme: ${if (result.isVerifiedUsingV2Scheme) "Aktif" else "Tidak"}")
                    appendLine("• v3 APK Signature Scheme: ${if (result.isVerifiedUsingV3Scheme) "Aktif" else "Tidak"}")
                    appendLine("• v4 APK Signature Scheme: ${if (result.isVerifiedUsingV4Scheme) "Aktif" else "Tidak"}")
                    appendLine("Jumlah Signer: ${signersList.size}")
                } else {
                    appendLine("Status: VERIFIKASI GAGAL ATAU TIDAK DITANDATANGANI")
                    if (errors.isNotEmpty()) {
                        appendLine("Kesalahan:")
                        errors.forEach { appendLine("- $it") }
                    }
                }
            }

            ApkVerificationDetails(
                isVerified = result.isVerified,
                isV1Scheme = result.isVerifiedUsingV1Scheme,
                isV2Scheme = result.isVerifiedUsingV2Scheme,
                isV3Scheme = result.isVerifiedUsingV3Scheme,
                isV4Scheme = result.isVerifiedUsingV4Scheme,
                signers = signersList,
                errors = errors,
                warnings = warnings,
                rawSummary = rawSummary
            )
        } catch (e: Exception) {
            ApkVerificationDetails(
                isVerified = false,
                isV1Scheme = false,
                isV2Scheme = false,
                isV3Scheme = false,
                isV4Scheme = false,
                signers = emptyList(),
                errors = listOf("Gagal membaca tanda tangan: ${e.message}"),
                warnings = emptyList(),
                rawSummary = "Error verifikasi: ${e.message}"
            )
        }
    }

    suspend fun processZipAndSign(
        context: Context,
        zipUri: Uri? = null,
        zipFileInput: File? = null,
        outputDir: File,
        keystoreFile: File,
        keystorePass: String,
        keyAlias: String,
        keyPass: String,
        enableV1: Boolean = true,
        enableV2: Boolean = true,
        enableV3: Boolean = true,
        minSdkVersion: Int = 24,
        onProgress: (String) -> Unit
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        val cachedZip = File(context.cacheDir, "temp_process_${System.currentTimeMillis()}.zip")
        var zipFileInstance: ZipFile? = null
        try {
            if (!outputDir.exists()) outputDir.mkdirs()

            onProgress("Membaca file arsip...")
            if (zipUri != null) {
                context.contentResolver.openInputStream(zipUri)?.use { input ->
                    FileOutputStream(cachedZip).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext Result.failure(Exception("Tidak dapat membaca file arsip dari URI."))
            } else if (zipFileInput != null && zipFileInput.exists()) {
                AppStorageManager.copyFile(zipFileInput, cachedZip)
            } else {
                return@withContext Result.failure(Exception("File arsip sumber tidak ditemukan."))
            }

            val signedApks = mutableListOf<File>()
            val zipFile = try {
                ZipFile(cachedZip).also { zipFileInstance = it }
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("File bukan format ZIP/APK yang valid: ${e.message}"))
            }

            val allEntries = zipFile.entries().asSequence().toList()
            val apkEntries = allEntries.filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
            val hasRootManifest = allEntries.any { 
                !it.isDirectory && (it.name.equals("AndroidManifest.xml", ignoreCase = true) || it.name.endsWith("/AndroidManifest.xml", ignoreCase = true)) 
            }

            if (apkEntries.isNotEmpty()) {
                var count = 0
                val total = apkEntries.size

                for (entry in apkEntries) {
                    count++
                    val entryFileName = entry.name.substringAfterLast("/")
                    val simpleName = entryFileName.substringBeforeLast(".").ifEmpty { "app_$count" }
                    onProgress("Mengekstrak ($count/$total): $entryFileName...")
                    val extractedApk = File(context.cacheDir, "ext_${System.currentTimeMillis()}_${count}_$entryFileName")
                    zipFile.getInputStream(entry).use { input ->
                        FileOutputStream(extractedApk).use { output ->
                            input.copyTo(output)
                        }
                    }

                    onProgress("Menandatangani ($count/$total): $simpleName...")
                    var signedApk = File(outputDir, "${simpleName}_signed.apk")
                    var counter = 1
                    while (signedApk.exists()) {
                        signedApk = File(outputDir, "${simpleName}_signed_$counter.apk")
                        counter++
                    }

                    val signResult = signApk(
                        inputApk = extractedApk,
                        outputApk = signedApk,
                        keystoreFile = keystoreFile,
                        keystorePass = keystorePass,
                        keyAlias = keyAlias,
                        keyPass = keyPass,
                        enableV1 = enableV1,
                        enableV2 = enableV2,
                        enableV3 = enableV3,
                        minSdkVersion = minSdkVersion
                    )

                    extractedApk.delete()

                    if (signResult.isSuccess) {
                        signedApks.add(signedApk)
                    } else {
                        return@withContext Result.failure(signResult.exceptionOrNull() ?: Exception("Gagal menandatangani ${entry.name}"))
                    }
                }
            } else if (hasRootManifest) {
                onProgress("Mendeteksi format APK langsung di dalam arsip...")
                val baseName = zipFileInput?.nameWithoutExtension ?: "app_${System.currentTimeMillis()}"
                var signedApk = File(outputDir, "${baseName}_signed.apk")
                var counter = 1
                while (signedApk.exists()) {
                    signedApk = File(outputDir, "${baseName}_signed_$counter.apk")
                    counter++
                }

                val signResult = signApk(
                    inputApk = cachedZip,
                    outputApk = signedApk,
                    keystoreFile = keystoreFile,
                    keystorePass = keystorePass,
                    keyAlias = keyAlias,
                    keyPass = keyPass,
                    enableV1 = enableV1,
                    enableV2 = enableV2,
                    enableV3 = enableV3,
                    minSdkVersion = minSdkVersion
                )

                if (signResult.isSuccess) {
                    signedApks.add(signedApk)
                } else {
                    return@withContext Result.failure(signResult.exceptionOrNull() ?: Exception("Gagal menandatangani APK di dalam arsip."))
                }
            } else {
                return@withContext Result.failure(
                    Exception("Tidak ditemukan file .apk maupun AndroidManifest.xml di dalam ZIP. Pastikan file berisi aplikasi Android (.apk).")
                )
            }

            Result.success(signedApks)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                zipFileInstance?.close()
            } catch (_: Exception) {}
            if (cachedZip.exists()) {
                cachedZip.delete()
            }
        }
    }

    private fun formatFingerprint(bytes: ByteArray): String {
        return bytes.joinToString(":") { "%02X".format(it) }
    }
}
