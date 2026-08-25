package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.logic.AppStorageManager
import com.example.logic.KeystoreManager
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.Security

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = [])
class ExampleRobolectricTest {

  @Before
  fun setUp() {
    Security.removeProvider("BC")
    Security.addProvider(BouncyCastleProvider())
  }

  @Test
  fun testAppNameString() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ApkSigner", appName)
  }

  @Test
  fun testKeystoreGenerationAndInspection() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val testKsFile = File(context.cacheDir, "test_generated.jks")
    if (testKsFile.exists()) testKsFile.delete()

    val result = KeystoreManager.generateKeystore(
      file = testKsFile,
      alias = "testalias",
      keystorePass = "password123",
      keyPass = "password123",
      commonName = "Test Developer",
      orgName = "Test Org",
      country = "ID"
    )

    assertTrue("Keystore generation should succeed", result.isSuccess)
    assertTrue("Keystore file must exist", testKsFile.exists())

    val inspectResult = KeystoreManager.inspectKeystore(testKsFile, "password123")
    assertTrue("Keystore inspection should succeed", inspectResult.isSuccess)
    val details = inspectResult.getOrThrow()
    assertEquals("testalias", details.firstAlias)
    assertTrue(details.sha256Fingerprint.isNotEmpty())
    assertTrue(details.sha1Fingerprint.isNotEmpty())
    assertTrue(details.md5Fingerprint.isNotEmpty())

    testKsFile.delete()
  }

  @Test
  fun testAppStorageDirs() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val signedDir = AppStorageManager.getSignedApksDir(context)
    val extractedDir = AppStorageManager.getExtractedDir(context)
    val keystoresDir = AppStorageManager.getKeystoresDir(context)

    assertNotNull(signedDir)
    assertNotNull(extractedDir)
    assertNotNull(keystoresDir)
  }
}

