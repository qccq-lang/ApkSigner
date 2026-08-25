package com.example.logic

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ExtractedFolderInfo(
    val directory: File,
    val name: String,
    val totalFiles: Int,
    val apkCount: Int,
    val totalSizeBytes: Long,
    val formattedSize: String,
    val lastModifiedFormatted: String
)

data class ExtractedFileItem(
    val file: File,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val formattedSize: String,
    val isApk: Boolean
)

object ZipExtractorLogic {

    /**
     * Extract a ZIP / APK / XAPK from Uri or File into the dedicated extracted directory.
     */
    suspend fun extractZip(
        context: Context,
        sourceUri: Uri,
        customFolderName: String? = null,
        onProgress: (currentFile: String, progress: Float, processedCount: Int, totalEstimate: Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val baseExtractedDir = AppStorageManager.getExtractedDir(context)
            if (!baseExtractedDir.exists()) baseExtractedDir.mkdirs()

            // Determine target folder name
            val originalName = getFileNameFromUri(context, sourceUri) ?: "archive_${System.currentTimeMillis()}"
            val cleanName = (customFolderName ?: originalName.substringBeforeLast(".")).replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            var targetDir = File(baseExtractedDir, cleanName)
            var counter = 1
            while (targetDir.exists() && (targetDir.listFiles()?.isNotEmpty() == true)) {
                targetDir = File(baseExtractedDir, "${cleanName}_$counter")
                counter++
            }
            targetDir.mkdirs()

            // Count total entries roughly if possible
            var totalEntries = 0
            try {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    ZipInputStream(input).use { zis ->
                        while (zis.nextEntry != null) {
                            totalEntries++
                            zis.closeEntry()
                        }
                    }
                }
            } catch (_: Exception) {
                totalEntries = 50 // fallback estimate
            }

            if (totalEntries <= 0) totalEntries = 50

            var processedCount = 0
            val targetCanonicalPath = targetDir.canonicalPath

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    val buffer = ByteArray(8192)

                    while (entry != null) {
                        val entryName = entry.name
                        val newFile = File(targetDir, entryName)

                        // Guard against Zip Slip vulnerability
                        val canonicalDestPath = newFile.canonicalPath
                        if (!canonicalDestPath.startsWith(targetCanonicalPath + File.separator) && canonicalDestPath != targetCanonicalPath) {
                            zis.closeEntry()
                            entry = zis.nextEntry
                            continue
                        }

                        if (entry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            // Ensure parent directory exists
                            newFile.parentFile?.mkdirs()
                            FileOutputStream(newFile).use { fos ->
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    fos.write(buffer, 0, len)
                                }
                            }
                        }

                        processedCount++
                        val progress = (processedCount.toFloat() / totalEntries.toFloat()).coerceIn(0f, 1f)
                        onProgress(entryName, progress, processedCount, totalEntries)

                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: return@withContext Result.failure(Exception("Cannot open ZIP input stream"))

            Result.success(targetDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * List all extracted folders in the dedicated directory
     */
    fun listExtractedFolders(context: Context): List<ExtractedFolderInfo> {
        val baseExtractedDir = AppStorageManager.getExtractedDir(context)
        val dirs = baseExtractedDir.listFiles { file -> file.isDirectory } ?: return emptyList()

        return dirs.map { dir ->
            val allFiles = dir.walkTopDown().filter { it.isFile }.toList()
            val totalSize = allFiles.sumOf { it.length() }
            val apkCount = allFiles.count { it.extension.equals("apk", ignoreCase = true) }
            val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
            val dateStr = dateFormat.format(java.util.Date(dir.lastModified()))

            ExtractedFolderInfo(
                directory = dir,
                name = dir.name,
                totalFiles = allFiles.size,
                apkCount = apkCount,
                totalSizeBytes = totalSize,
                formattedSize = AppStorageManager.formatFileSize(totalSize),
                lastModifiedFormatted = dateStr
            )
        }.sortedByDescending { it.directory.lastModified() }
    }

    /**
     * List files inside a specific extracted folder
     */
    fun listFilesInsideFolder(folder: File): List<ExtractedFileItem> {
        if (!folder.exists() || !folder.isDirectory) return emptyList()

        return folder.walkTopDown().maxDepth(5).map { file ->
            val relativePath = file.relativeTo(folder).path
            val isApk = file.isFile && file.extension.equals("apk", ignoreCase = true)
            ExtractedFileItem(
                file = file,
                relativePath = if (relativePath.isEmpty()) file.name else relativePath,
                isDirectory = file.isDirectory,
                sizeBytes = if (file.isFile) file.length() else 0L,
                formattedSize = if (file.isFile) AppStorageManager.formatFileSize(file.length()) else "Folder",
                isApk = isApk
            )
        }.filter { it.relativePath.isNotEmpty() && it.relativePath != "." }.sortedWith(
            compareBy<ExtractedFileItem> { !it.isDirectory }
                .thenBy { !it.isApk }
                .thenBy { it.relativePath }
        ).toList()
    }

    /**
     * Helper to get file name from Uri
     */
    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) name = cursor.getString(idx)
                }
            }
        } catch (_: Exception) {}
        return name ?: uri.lastPathSegment
    }
}
