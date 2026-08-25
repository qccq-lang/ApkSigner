package com.example.logic

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ApkMetadata(
    val file: File,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val permissions: List<String>,
    val fileSizeFormatted: String,
    val lastModifiedFormatted: String,
    val sha256: String,
    val icon: Drawable? = null
)

object AppStorageManager {

    private const val BASE_FOLDER = "ApkSigner"
    private const val SIGNED_FOLDER = "signed"
    private const val EXTRACTED_FOLDER = "extracted"
    private const val KEYSTORES_FOLDER = "keystores"

    /**
     * Get root folder for the app in external storage if available and writable,
     * otherwise fallback to app-specific external files dir or internal files dir.
     */
    fun getBaseDir(context: Context): File {
        // Try public external storage (e.g. /storage/emulated/0/ApkSigner) if fully accessible
        try {
            val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true
            }
            if (hasAllFilesAccess && Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
                val externalStorage = Environment.getExternalStorageDirectory()
                val dir = File(externalStorage, BASE_FOLDER)
                if (dir.exists() || dir.mkdirs()) {
                    val probe = File(dir, ".probe_${System.currentTimeMillis()}")
                    if (probe.createNewFile()) {
                        probe.delete()
                        return dir
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback to app-specific external storage (always writable without special permissions)
        try {
            val extFiles = context.getExternalFilesDir(null)
            if (extFiles != null) {
                val dir = File(extFiles, BASE_FOLDER)
                if (dir.exists() || dir.mkdirs()) {
                    return dir
                }
            }
        } catch (_: Exception) {}

        // Fallback to internal storage
        val internalDir = File(context.filesDir, BASE_FOLDER)
        if (!internalDir.exists()) internalDir.mkdirs()
        return internalDir
    }

    /**
     * Dedicated folder for Signed APKs
     */
    fun getSignedApksDir(context: Context): File {
        val dir = File(getBaseDir(context), SIGNED_FOLDER)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Dedicated folder for Extracted ZIP/APK files
     */
    fun getExtractedDir(context: Context): File {
        val dir = File(getBaseDir(context), EXTRACTED_FOLDER)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Folder for stored Keystores
     */
    fun getKeystoresDir(context: Context): File {
        val dir = File(getBaseDir(context), KEYSTORES_FOLDER)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * List all signed APK files, sorted with newest first
     */
    fun listSignedApks(context: Context): List<File> {
        val dir = getSignedApksDir(context)
        return dir.listFiles { file -> file.isFile && file.name.endsWith(".apk", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Extract detailed metadata from an APK file
     */
    fun getApkMetadata(context: Context, apkFile: File): ApkMetadata {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS
        var pkgInfo: PackageInfo? = null
        try {
            pkgInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val appName: String
        val packageName: String
        val versionName: String
        val versionCode: Long
        var icon: Drawable? = null
        var minSdk = 0
        var targetSdk = 0
        val permissions = mutableListOf<String>()

        if (pkgInfo != null) {
            val appInfo: ApplicationInfo? = pkgInfo.applicationInfo
            if (appInfo != null) {
                appInfo.sourceDir = apkFile.absolutePath
                appInfo.publicSourceDir = apkFile.absolutePath

                appName = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    apkFile.nameWithoutExtension
                }

                icon = try {
                    pm.getApplicationIcon(appInfo)
                } catch (e: Exception) {
                    null
                }

                targetSdk = appInfo.targetSdkVersion
                minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appInfo.minSdkVersion
                } else {
                    0
                }
            } else {
                appName = apkFile.nameWithoutExtension
            }

            packageName = pkgInfo.packageName ?: "Unknown"
            versionName = pkgInfo.versionName ?: "1.0"
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }

            pkgInfo.requestedPermissions?.let {
                permissions.addAll(it)
            }
        } else {
            appName = apkFile.nameWithoutExtension
            packageName = "com.unknown.apk"
            versionName = "1.0"
            versionCode = 1
        }

        val fileSizeFormatted = formatFileSize(apkFile.length())
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val lastModifiedFormatted = dateFormat.format(Date(apkFile.lastModified()))
        val sha256 = calculateHash(apkFile, "SHA-256")

        return ApkMetadata(
            file = apkFile,
            appName = appName,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            permissions = permissions,
            fileSizeFormatted = fileSizeFormatted,
            lastModifiedFormatted = lastModifiedFormatted,
            sha256 = sha256,
            icon = icon
        )
    }

    /**
     * Install APK using Intent & FileProvider
     */
    fun installApk(context: Context, apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists()) {
                return Result.failure(Exception("File APK tidak ditemukan: ${apkFile.absolutePath}"))
            }

            // Check Unknown Sources Permission for Android O+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Share file via system share sheet
     */
    fun shareFile(context: Context, file: File, mimeType: String = "*/*") {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Copy file to another location
     */
    fun copyFile(source: File, dest: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * Format bytes to human readable string (KB, MB, GB)
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val formatted = String.format(Locale.US, "%.2f", bytes / Math.pow(1024.0, digitGroups.toDouble()))
        return "$formatted ${units[digitGroups.coerceIn(0, units.size - 1)]}"
    }

    /**
     * Calculate hash of file (MD5, SHA-1, SHA-256)
     */
    fun calculateHash(file: File, algorithm: String): String {
        return try {
            val digest = MessageDigest.getInstance(algorithm)
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
