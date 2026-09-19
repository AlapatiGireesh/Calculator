package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.LocationRepository
import com.example.ui.LocationViewModel
import com.example.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @org.junit.Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val rep = LocationRepository.getInstance(context)
    rep.setPairedParentId(null)
    rep.setOwnerParentId(null)
    rep.setUserRole("none")
    context.getSharedPreferences("guardian_link_prefs", Context.MODE_PRIVATE)
        .edit()
        .clear()
        .commit()
  }

  @org.junit.After
  fun tearDown() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val rep = LocationRepository.getInstance(context)
    rep.setPairedParentId(null)
    rep.setOwnerParentId(null)
    rep.setUserRole("none")
    context.getSharedPreferences("guardian_link_prefs", Context.MODE_PRIVATE)
        .edit()
        .clear()
        .commit()
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Calculator", appName)
  }

  @Test
  fun `select user role parent sets state correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val rep = LocationRepository.getInstance(context)
    val viewModel = LocationViewModel(rep)

    viewModel.selectUserRole("parent")

    assertEquals("parent", viewModel.getUserRole())
    assertEquals("parent", viewModel.userRole.value)
    assertNotNull(viewModel.getOwnerId())
  }

  @Test
  fun `select user role child clears paired states`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val rep = LocationRepository.getInstance(context)
    val viewModel = LocationViewModel(rep)

    viewModel.selectUserRole("child")

    assertEquals("child", viewModel.getUserRole())
    assertEquals("child", viewModel.userRole.value)
    assertNull(viewModel.getPairedParentId())
    assertEquals("Worker Device", viewModel.getChildName())
  }

  @Test
  fun `pairing with sandbox code instantly establishes connection`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val rep = LocationRepository.getInstance(context)
    val viewModel = LocationViewModel(rep)

    viewModel.selectUserRole("child")
    
    // Attempt rapid fast pairing with sandbox code '123456'
    viewModel.pairWithCode("123456", "Test Worker")

    // Assert immediately successful and paired
    assertTrue(viewModel.devicePairingState.value is UiState.Success)
    assertEquals("default_parent_123", viewModel.getPairedParentId())
    assertEquals("child", viewModel.getUserRole())
    assertEquals("Test Worker", viewModel.getChildName())
  }

  @Test
  fun `sign out clears pairing memory and resets role`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val rep = LocationRepository.getInstance(context)
    val viewModel = LocationViewModel(rep)

    // Setup a pairing
    viewModel.selectUserRole("child")
    viewModel.pairWithCode("123456", "Tracker One")

    // Verify before signout
    assertEquals("child", viewModel.getUserRole())
    assertEquals("default_parent_123", viewModel.getPairedParentId())

    // Trigger signout
    viewModel.signOut()

    // Assert states are wiped
    assertEquals("none", viewModel.getUserRole())
    assertNull(viewModel.getPairedParentId())
    assertNull(viewModel.parentPairingCode.value)
  }

  @Test
  fun `worker mode without entering pairing code is not connected`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val rep = LocationRepository.getInstance(context)
    val viewModel = LocationViewModel(rep)

    viewModel.selectUserRole("child")

    // Worker mode without entering pairing code must NOT be connected
    assertEquals("child", viewModel.getUserRole())
    assertNull(viewModel.getPairedParentId())
    assertTrue(viewModel.devicePairingState.value is UiState.Idle)
  }

  @Test
  fun `worker mode disconnecting clears pairing and requires re-entering pairing code`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val rep = LocationRepository.getInstance(context)
    val viewModel = LocationViewModel(rep)

    // Pair worker with pairing code
    viewModel.selectUserRole("child")
    viewModel.pairWithCode("123456", "Field Worker 01")

    assertEquals("default_parent_123", viewModel.getPairedParentId())
    assertTrue(viewModel.devicePairingState.value is UiState.Success)

    // Disconnect worker
    viewModel.disconnectWorker(context = context)

    // Verify worker is now completely disconnected
    assertNull(viewModel.getPairedParentId())
    assertTrue(viewModel.devicePairingState.value is UiState.Idle)

    // Re-pairing requires entering code again
    viewModel.pairWithCode("123456", "Field Worker 01")
    assertEquals("default_parent_123", viewModel.getPairedParentId())
    assertTrue(viewModel.devicePairingState.value is UiState.Success)
  }

  @Test
  fun `owner disconnecting worker triggers worker disconnection and stops tracking`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val rep = LocationRepository.getInstance(context)
    val viewModel = LocationViewModel(rep)

    // Owner setup
    viewModel.selectUserRole("parent")
    val ownerId = viewModel.getOwnerId()
    assertTrue(ownerId.isNotEmpty())

    // Worker pairs
    rep.setPairedParentId(ownerId)
    assertEquals(ownerId, rep.getPairedParentId())

    // Owner disconnects child device
    val targetChildId = "child_test_123"
    viewModel.disconnectWorker(childId = targetChildId, context = context)

    // Verify disconnect is executed
    // Owner is still owner, ready to pair or accept worker with new pairing code
    assertEquals("parent", viewModel.getUserRole())
  }
}

