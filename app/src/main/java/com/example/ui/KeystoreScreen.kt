package com.example.ui

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.logic.AppStorageManager
import com.example.logic.KeystoreDetails
import com.example.logic.KeystoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeystoreScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var keystores by remember { mutableStateOf<List<File>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showInspectDialogForFile by remember { mutableStateOf<File?>(null) }
    var keystoreToDelete by remember { mutableStateOf<File?>(null) }

    fun refreshList() {
        keystores = KeystoreManager.listKeystores(context)
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                KeystoreManager.getOrCreateTestKey(context)
            } catch (_: Exception) {}
        }
        refreshList()
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    var fileName = "imported_${System.currentTimeMillis()}.jks"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx != -1) fileName = cursor.getString(idx)
                        }
                    }
                    val destFile = File(AppStorageManager.getKeystoresDir(context), fileName)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    refreshList()
                    Toast.makeText(context, "Keystore '$fileName' berhasil diimpor", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Gagal mengimpor: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = "Import Keystore")
                }
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Keystore")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Header Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
            ) {
                Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Pengelolaan Keystore",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            "Buat, impor, ekspor, dan inspeksi fingerprint SHA-1/SHA-256",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Text(
                "Daftar Keystore Tersimpan (${keystores.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (keystores.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(60.dp), tint = MaterialTheme.colorScheme.outline)
                        Text("Belum ada keystore tersimpan.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = { showCreateDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Buat Keystore Baru", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(keystores, key = { it.absolutePath }) { file ->
                        val isTestKey = file.name.contains("testkey", ignoreCase = true)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .background(
                                                if (isTestKey) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                                RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.VpnKey,
                                            contentDescription = null,
                                            tint = if (isTestKey) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                file.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isTestKey) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        "AOSP TestKey",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            "${AppStorageManager.formatFileSize(file.length())} • ${file.name.substringAfterLast(".").uppercase()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                // Quick Actions
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { showInspectDialogForFile = file },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Inspeksi Sertifikat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedIconButton(
                                        onClick = {
                                            AppStorageManager.shareFile(context, file, "application/octet-stream")
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    if (!isTestKey) {
                                        OutlinedIconButton(
                                            onClick = { keystoreToDelete = file },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.size(38.dp),
                                            colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
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

    // Inspect Keystore Dialog
    showInspectDialogForFile?.let { file ->
        InspectKeystoreDialog(
            file = file,
            onDismiss = { showInspectDialogForFile = null }
        )
    }

    // Create Keystore Dialog
    if (showCreateDialog) {
        CreateKeystoreDialog(
            onDismiss = { showCreateDialog = false },
            onCreated = {
                refreshList()
                showCreateDialog = false
            }
        )
    }

    // Delete Keystore Dialog
    keystoreToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { keystoreToDelete = null },
            title = { Text("Hapus Keystore?", fontWeight = FontWeight.Bold) },
            text = { Text("Apakah Anda yakin ingin menghapus '${file.name}'? File ini tidak dapat dikembalikan.") },
            confirmButton = {
                Button(
                    onClick = {
                        file.delete()
                        keystoreToDelete = null
                        refreshList()
                        Toast.makeText(context, "Keystore berhasil dihapus", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { keystoreToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun InspectKeystoreDialog(
    file: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isTestKey = file.name.contains("testkey", ignoreCase = true)
    var password by remember { mutableStateOf(if (isTestKey) "android" else "") }
    var details by remember { mutableStateOf<KeystoreDetails?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isInspecting by remember { mutableStateOf(false) }

    fun doInspect() {
        isInspecting = true
        errorMsg = null
        val res = KeystoreManager.inspectKeystore(file, password)
        isInspecting = false
        if (res.isSuccess) {
            details = res.getOrThrow()
        } else {
            errorMsg = res.exceptionOrNull()?.message ?: "Gagal membaca Keystore. Pastikan password benar."
        }
    }

    LaunchedEffect(Unit) {
        if (isTestKey) {
            doInspect()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text("Inspeksi Keystore", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("File: ${file.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)

                if (details == null) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password Keystore") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (errorMsg != null) {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = { doInspect() },
                        enabled = !isInspecting && password.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            if (isInspecting) "Membaca..." else "Buka & Analisis Sertifikat",
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    val d = details!!
                    DetailItem("Alias Kunci", d.aliases.joinToString(", "))
                    DetailItem("Algoritma", "${d.algorithm} (${d.keySize})")
                    DetailItem("Masa Berlaku", "${d.validFrom} s/d ${d.validUntil}")
                    if (d.isExpired) {
                        Text("⚠️ Sertifikat sudah kadaluarsa!", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                    DetailItem("Subject DN", d.subject)
                    DetailItem("Issuer DN", d.issuer)

                    Spacer(Modifier.height(4.dp))
                    Text("Fingerprint Sertifikat:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    FingerprintCopyRow("SHA-256", d.sha256Fingerprint, clipboardManager, context)
                    FingerprintCopyRow("SHA-1", d.sha1Fingerprint, clipboardManager, context)
                    FingerprintCopyRow("MD5", d.md5Fingerprint, clipboardManager, context)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun FingerprintCopyRow(
    label: String,
    value: String,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    context: android.content.Context
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    clipboardManager.setText(AnnotatedString(value))
                    Toast.makeText(context, "$label disalin ke clipboard", Toast.LENGTH_SHORT).show()
                }
        ) {
            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateKeystoreDialog(
    onDismiss: () -> Unit,
    onCreated: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var filename by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var keystorePass by remember { mutableStateOf("") }
    var keyPass by remember { mutableStateOf("") }
    var samePassword by remember { mutableStateOf(true) }

    var commonName by remember { mutableStateOf("") }
    var orgUnit by remember { mutableStateOf("") }
    var orgName by remember { mutableStateOf("") }
    var locality by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("ID") }
    var validityYears by remember { mutableStateOf("25") }

    var selectedAlgorithm by remember { mutableStateOf("RSA 2048") }
    var showAdvanced by remember { mutableStateOf(false) }

    var isCreating by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AddModerator, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text("Buat Keystore Baru", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = filename,
                    onValueChange = { filename = it },
                    label = { Text("Nama File (cth: release.jks)") },
                    placeholder = { Text("release.jks") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Key Alias (cth: key0)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = keystorePass,
                    onValueChange = {
                        keystorePass = it
                        if (samePassword) keyPass = it
                    },
                    label = { Text("Keystore Password") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle password"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        samePassword = !samePassword
                        if (samePassword) keyPass = keystorePass
                    }
                ) {
                    Checkbox(
                        checked = samePassword,
                        onCheckedChange = {
                            samePassword = it
                            if (samePassword) keyPass = keystorePass
                        }
                    )
                    Text("Gunakan password yang sama untuk Key", style = MaterialTheme.typography.bodySmall)
                }

                if (!samePassword) {
                    OutlinedTextField(
                        value = keyPass,
                        onValueChange = { keyPass = it },
                        label = { Text("Key Password") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = commonName,
                    onValueChange = { commonName = it },
                    label = { Text("Nama Pemilik (CN) *") },
                    placeholder = { Text("Nama Anda / Perusahaan") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Advanced Fields Toggle
                TextButton(
                    onClick = { showAdvanced = !showAdvanced },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showAdvanced) "Sembunyikan Opsi Lanjutan ▲" else "Tampilkan Opsi Lanjutan (OU, O, L, C, Algoritma) ▼")
                }

                if (showAdvanced) {
                    OutlinedTextField(
                        value = orgUnit,
                        onValueChange = { orgUnit = it },
                        label = { Text("Unit Organisasi (OU)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = orgName,
                        onValueChange = { orgName = it },
                        label = { Text("Nama Organisasi (O)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = locality,
                            onValueChange = { locality = it },
                            label = { Text("Kota (L)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = country,
                            onValueChange = { country = it },
                            label = { Text("Negara (C)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = validityYears,
                        onValueChange = { validityYears = it },
                        label = { Text("Masa Berlaku (Tahun)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isCreating = true
                    errorMsg = null
                    coroutineScope.launch {
                        val cleanFilename = if (filename.contains(".")) filename else "$filename.jks"
                        val targetFile = File(AppStorageManager.getKeystoresDir(context), cleanFilename)
                        val actualKeyPass = if (samePassword) keystorePass else keyPass
                        val years = validityYears.toIntOrNull() ?: 25

                        val res = withContext(Dispatchers.IO) {
                            KeystoreManager.generateKeystore(
                                file = targetFile,
                                alias = alias,
                                keystorePass = keystorePass,
                                keyPass = actualKeyPass,
                                commonName = commonName.ifBlank { "Android Developer" },
                                orgUnit = orgUnit,
                                orgName = orgName,
                                locality = locality,
                                state = state,
                                country = country,
                                validityYears = years,
                                algorithm = "RSA",
                                keySize = 2048
                            )
                        }
                        isCreating = false

                        if (res.isSuccess) {
                            Toast.makeText(context, "Keystore '$cleanFilename' berhasil dibuat!", Toast.LENGTH_SHORT).show()
                            onCreated()
                        } else {
                            errorMsg = res.exceptionOrNull()?.message ?: "Gagal membuat Keystore"
                        }
                    }
                },
                enabled = !isCreating && filename.isNotBlank() && alias.isNotBlank() && keystorePass.isNotBlank() && (samePassword || keyPass.isNotBlank()),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Text(if (isCreating) "Membuat..." else "Buat Keystore", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCreating) {
                Text("Batal")
            }
        }
    )
}
