package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.ui.KeystoreScreen
import com.example.ui.SignScreen
import com.example.ui.SignedApksScreen
import com.example.ui.VerifyScreen
import com.example.ui.ZipExtractScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.io.FileOutputStream
import java.security.Security

class MainActivity : ComponentActivity() {

    private val _incomingFile = MutableStateFlow<File?>(null)
    private val incomingFile: StateFlow<File?> = _incomingFile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Register BouncyCastle
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }

        requestStoragePermission()
        handleIntent(intent)

        setContent {
            MyApplicationTheme {
                val fileFromIntent by incomingFile.collectAsStateWithLifecycle()
                ApkSignerApp(
                    initialFile = fileFromIntent,
                    onFileConsumed = { _incomingFile.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND) {
            val uri = intent.data ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val file = copyUriToCache(uri)
                    _incomingFile.value = file
                }
            }
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        try {
            var name = "shared_file"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) name = cursor.getString(idx)
                }
            }
            if (!name.contains(".")) {
                val type = contentResolver.getType(uri)
                if (type == "application/zip") name += ".zip"
                else if (type == "application/vnd.android.package-archive") name += ".apk"
            }
            val cacheFile = File(cacheDir, name)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            return cacheFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                    startActivity(intent)
                } catch (_: Exception) {
                    val intent = Intent()
                    intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    startActivity(intent)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkSignerApp(initialFile: File? = null, onFileConsumed: () -> Unit = {}) {
    var currentTab by remember { mutableIntStateOf(0) }
    var fileToSign by remember { mutableStateOf<File?>(null) }
    var fileToVerify by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(initialFile) {
        if (initialFile != null) {
            val extension = initialFile.extension.lowercase()
            if (extension == "zip" || extension == "apk") {
                fileToSign = initialFile
                currentTab = 0
            } else {
                fileToSign = initialFile
                currentTab = 0
            }
            onFileConsumed()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "ApkSigner Pro",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            "ZIP Extractor • Keystore Manager • APK Installer",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "v1/v2/v3",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            val navItemColors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 6.dp,
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                // 1. Sign Tab
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(if (currentTab == 0) Icons.Filled.Create else Icons.Outlined.Create, contentDescription = "Sign") },
                    label = { Text("Sign", fontSize = 11.sp, fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Medium) },
                    colors = navItemColors
                )

                // 2. Signed APKs Tab (with Install button)
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(if (currentTab == 1) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle, contentDescription = "Signed APKs") },
                    label = { Text("Signed", fontSize = 11.sp, fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Medium) },
                    colors = navItemColors
                )

                // 3. Extract ZIP Tab
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(if (currentTab == 2) Icons.Filled.FolderZip else Icons.Outlined.FolderZip, contentDescription = "Ekstrak") },
                    label = { Text("Ekstrak", fontSize = 11.sp, fontWeight = if (currentTab == 2) FontWeight.Bold else FontWeight.Medium) },
                    colors = navItemColors
                )

                // 4. Keystore Tab
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(if (currentTab == 3) Icons.Filled.VpnKey else Icons.Outlined.VpnKey, contentDescription = "Keystore") },
                    label = { Text("Keystore", fontSize = 11.sp, fontWeight = if (currentTab == 3) FontWeight.Bold else FontWeight.Medium) },
                    colors = navItemColors
                )

                // 5. Verify Tab
                NavigationBarItem(
                    selected = currentTab == 4,
                    onClick = { currentTab = 4 },
                    icon = { Icon(if (currentTab == 4) Icons.Filled.VerifiedUser else Icons.Outlined.VerifiedUser, contentDescription = "Verify") },
                    label = { Text("Verify", fontSize = 11.sp, fontWeight = if (currentTab == 4) FontWeight.Bold else FontWeight.Medium) },
                    colors = navItemColors
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (currentTab) {
                0 -> SignScreen(
                    preselectedFile = fileToSign,
                    onNavigateToSignedTab = {
                        currentTab = 1
                    }
                )
                1 -> SignedApksScreen(
                    onNavigateToSign = {
                        currentTab = 0
                    }
                )
                2 -> ZipExtractScreen(
                    onSignApkFile = { apkFile ->
                        fileToSign = apkFile
                        currentTab = 0
                    }
                )
                3 -> KeystoreScreen()
                4 -> VerifyScreen(
                    preselectedFile = fileToVerify
                )
            }
        }
    }
}
