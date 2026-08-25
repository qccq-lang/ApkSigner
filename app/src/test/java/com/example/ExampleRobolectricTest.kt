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
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun testAppNameString() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ApkSigner", appName)
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

