package com.example.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.logic.AppStorageManager
import com.example.logic.KeystoreManager
import com.example.logic.SignerLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignScreen(
    preselectedFile: File? = null,
    onNavigateToSignedTab: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var isZipFile by remember { mutableStateOf(false) }

    // Key Mode: 0 = TestKey (AOSP Debug), 1 = Custom Keystore
    var keyMode by remember { mutableStateOf(0) }

    var selectedKeystore by remember { mutableStateOf<File?>(null) }
    var keystorePass by remember { mutableStateOf("") }
    var keyAlias by remember { mutableStateOf("") }
    var keyPass by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Schemes
    var enableV1 by remember { mutableStateOf(true) }
    var enableV2 by remember { mutableStateOf(true) }
    var enableV3 by remember { mutableStateOf(true) }
    var showSchemeOptions by remember { mutableStateOf(false) }

    // Execution State
    var isSigning by remember { mutableStateOf(false) }
    var progressMsg by remember { mutableStateOf("") }
    var signedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val keystores = remember { KeystoreManager.listKeystores(context) }
    var keystoreDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(preselectedFile) {
        if (preselectedFile != null && preselectedFile.exists()) {
            selectedFileName = preselectedFile.name
            val ext = preselectedFile.extension.lowercase()
            isZipFile = ext in listOf("zip", "xapk", "apks") || preselectedFile.name.endsWith(".zip", ignoreCase = true)
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            var name = "archive_or_apk"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) name = cursor.getString(idx)
                    }
                }
            } catch (_: Exception) {}
            selectedFileName = name
            val type = context.contentResolver.getType(uri) ?: ""
            val ext = name.substringAfterLast(".", "").lowercase()
            isZipFile = type == "application/zip" || type == "application/x-zip-compressed" || ext in listOf("zip", "xapk", "apks") || name.endsWith(".zip", ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Create, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Tandatangani APK / ZIP",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Mendukung skema tanda tangan v1, v2, & v3",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }

                Button(
                    onClick = {
                        filePicker.launch(arrayOf("application/vnd.android.package-archive", "application/zip", "application/octet-stream", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (selectedFileName.isNotEmpty()) "Ganti File ($selectedFileName)" else "Pilih File .APK atau .ZIP",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (selectedFileName.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            if (isZipFile) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                            RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (isZipFile) Icons.Default.FolderZip else Icons.Default.Android,
                                        contentDescription = null,
                                        tint = if (isZipFile) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        selectedFileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (isZipFile) "Format Arsip ZIP (Tanda tangani APK di dalamnya)" else "Format APK Tunggal",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = !isZipFile,
                                    onClick = { isZipFile = false },
                                    label = { Text("APK Tunggal", fontSize = 11.sp) },
                                    leadingIcon = {
                                        if (!isZipFile) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                )
                                FilterChip(
                                    selected = isZipFile,
                                    onClick = { isZipFile = true },
                                    label = { Text("Arsip ZIP", fontSize = 11.sp) },
                                    leadingIcon = {
                                        if (isZipFile) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Keystore Selection Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Metode Kunci Penandatangan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Key Mode Selector (TestKey vs Custom)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = keyMode == 0,
                        onClick = { keyMode = 0 },
                        label = {
                            Text(
                                "AOSP TestKey",
                                fontWeight = if (keyMode == 0) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (keyMode == 0) Icons.Default.Check else Icons.Default.FlashOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = keyMode == 1,
                        onClick = { keyMode = 1 },
                        label = {
                            Text(
                                "Custom Keystore",
                                fontWeight = if (keyMode == 1) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (keyMode == 1) Icons.Default.Check else Icons.Default.VpnKey,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (keyMode == 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Menggunakan standar debug testkey bawaan Android. Cepat & tidak memerlukan konfigurasi password manual.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                } else {
                    // Custom Keystore Inputs
                    ExposedDropdownMenuBox(
                        expanded = keystoreDropdownExpanded,
                        onExpandedChange = { keystoreDropdownExpanded = !keystoreDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedKeystore?.name ?: "Pilih Keystore Tersimpan",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = keystoreDropdownExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = keystoreDropdownExpanded,
                            onDismissRequest = { keystoreDropdownExpanded = false }
                        ) {
                            keystores.forEach { file ->
                                DropdownMenuItem(
                                    text = { Text(file.name) },
                                    onClick = {
                                        selectedKeystore = file
                                        keystoreDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = keystorePass,
                        onValueChange = { keystorePass = it },
                        label = { Text("Keystore Password") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = keyAlias,
                        onValueChange = { keyAlias = it },
                        label = { Text("Key Alias") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = keyPass,
                        onValueChange = { keyPass = it },
                        label = { Text("Key Password") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Scheme Options Collapsible
                OutlinedButton(
                    onClick = { showSchemeOptions = !showSchemeOptions },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (showSchemeOptions) "Sembunyikan Opsi Skema ▲" else "Opsi Skema Tanda Tangan (v1, v2, v3) ▼",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (showSchemeOptions) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = enableV1, onCheckedChange = { enableV1 = it })
                            Text("v1 Signature Scheme (JAR Signing)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = enableV2, onCheckedChange = { enableV2 = it })
                            Text("v2 Signature Scheme (APK v2)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = enableV3, onCheckedChange = { enableV3 = it })
                            Text("v3 Signature Scheme (APK v3)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        // Sign Button
        val canSign = (selectedUri != null || preselectedFile != null) &&
                (keyMode == 0 || (selectedKeystore != null && keystorePass.isNotEmpty() && keyAlias.isNotEmpty() && keyPass.isNotEmpty()))

        Button(
            onClick = {
                isSigning = true
                errorMsg = null
                signedFiles = emptyList()
                progressMsg = "Mempersiapkan penandatanganan..."

                coroutineScope.launch {
                    val outputDir = withContext(Dispatchers.IO) {
                        AppStorageManager.getSignedApksDir(context)
                    }

                    val targetKeystoreFile = try {
                        withContext(Dispatchers.IO) {
                            if (keyMode == 0) {
                                KeystoreManager.getOrCreateTestKey(context)
                            } else {
                                selectedKeystore!!
                            }
                        }
                    } catch (e: Exception) {
                        isSigning = false
                        errorMsg = "Gagal memuat/menyiapkan Keystore: ${e.message}"
                        return@launch
                    }

                    val actualKeystorePass = if (keyMode == 0) "android" else keystorePass
                    val actualKeyAlias = if (keyMode == 0) "androiddebugkey" else keyAlias
                    val actualKeyPass = if (keyMode == 0) "android" else keyPass

                    val uri = selectedUri
                    val localFile = preselectedFile

                    if (isZipFile) {
                        val result = SignerLogic.processZipAndSign(
                            context = context,
                            zipUri = uri,
                            zipFileInput = localFile,
                            outputDir = outputDir,
                            keystoreFile = targetKeystoreFile,
                            keystorePass = actualKeystorePass,
                            keyAlias = actualKeyAlias,
                            keyPass = actualKeyPass,
                            enableV1 = enableV1,
                            enableV2 = enableV2,
                            enableV3 = enableV3,
                            minSdkVersion = 24,
                            onProgress = { progressMsg = it }
                        )
                        isSigning = false
                        if (result.isSuccess) {
                            signedFiles = result.getOrThrow()
                            progressMsg = "Penandatanganan selesai!"
                            Toast.makeText(context, "Berhasil menandatangani ${signedFiles.size} APK!", Toast.LENGTH_SHORT).show()
                        } else {
                            errorMsg = result.exceptionOrNull()?.message ?: "Gagal menandatangani ZIP"
                        }
                    } else {
                        // Direct APK
                        val cachedApk = File(context.cacheDir, "temp_sign_${System.currentTimeMillis()}.apk")
                        try {
                            progressMsg = "Mengekstrak dan memproses APK..."

                            if (uri != null) {
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    FileOutputStream(cachedApk).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            } else if (localFile != null && localFile.exists()) {
                                AppStorageManager.copyFile(localFile, cachedApk)
                            }

                            val cleanBaseName = selectedFileName.substringBeforeLast(".").ifEmpty { "app" }
                            val outApk = File(outputDir, "${cleanBaseName}_signed.apk")

                            progressMsg = "Menandatangani APK dengan ${if (keyMode == 0) "TestKey" else targetKeystoreFile.name}..."

                            val res = withContext(Dispatchers.IO) {
                                SignerLogic.signApk(
                                    inputApk = cachedApk,
                                    outputApk = outApk,
                                    keystoreFile = targetKeystoreFile,
                                    keystorePass = actualKeystorePass,
                                    keyAlias = actualKeyAlias,
                                    keyPass = actualKeyPass,
                                    enableV1 = enableV1,
                                    enableV2 = enableV2,
                                    enableV3 = enableV3,
                                    minSdkVersion = 24
                                )
                            }
                            isSigning = false

                            if (res.isSuccess) {
                                signedFiles = listOf(outApk)
                                progressMsg = "Berhasil ditandatangani!"
                                Toast.makeText(context, "APK berhasil ditandatangani!", Toast.LENGTH_SHORT).show()
                            } else {
                                errorMsg = res.exceptionOrNull()?.message ?: "Gagal menandatangani APK"
                            }
                        } catch (e: Exception) {
                            isSigning = false
                            errorMsg = e.message
                        } finally {
                            if (cachedApk.exists()) {
                                cachedApk.delete()
                            }
                        }
                    }
                }
            },
            enabled = !isSigning && canSign,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            ),
            contentPadding = PaddingValues(16.dp)
        ) {
            if (isSigning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(progressMsg, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(Icons.Default.BorderColor, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Tandatangani Sekarang", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimary)
            }
        }

        if (errorMsg != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(10.dp))
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Output Results
        if (signedFiles.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("HASIL PENANDATANGANAN", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = onNavigateToSignedTab) {
                            Text("Lihat Semua APK", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    signedFiles.forEach { file ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Android, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            file.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${AppStorageManager.formatFileSize(file.length())} • Tersimpan di /ApkSigner/signed/",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Install Button
                                    Button(
                                        onClick = {
                                            val res = AppStorageManager.installApk(context, file)
                                            if (res.isFailure) {
                                                Toast.makeText(context, "Install error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("INSTALL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    // Share Button
                                    OutlinedButton(
                                        onClick = {
                                            AppStorageManager.shareFile(context, file, "application/vnd.android.package-archive")
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Share", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
