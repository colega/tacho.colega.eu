package eu.colega.tacho.app.ui

import androidx.test.core.app.ApplicationProvider
import eu.colega.tacho.app.data.CardRepository
import eu.colega.tacho.core.tachograph.DriverCardDump
import eu.colega.tacho.core.tachograph.Layout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

/**
 * Verifies the shared card view model state machine against a synthetic
 * amended generation 2 dump (17-byte Application_Identification).
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class CardViewModelTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val repository = CardRepository()
    private val ioDispatcher = UnconfinedTestDispatcher()
    private val viewModel = CardViewModel(repository, context, ioDispatcher)

    @Test
    fun `parsing a valid dump publishes the card and clears errors`() {
        viewModel.setReading(true)
        viewModel.onDumpRead(g2Dump())

        val state = viewModel.state.value
        assertFalse(state.isReading)
        assertNull(state.error)
        assertTrue(state.card!!.isV2)
        assertEquals(0x0501, Layout.EF_APPLICATION_IDENTIFICATION)
    }

    @Test
    fun `a missing application identification surfaces as a user error`() {
        viewModel.onDumpRead(DriverCardDump.of())
        val state = viewModel.state.value
        assertNull(state.card)
        assertTrue(state.error!!.contains("Application_Identification"))
    }

    @Test
    fun `a read failure message lands in the state`() {
        viewModel.onReadError("NFC link lost")
        assertEquals("NFC link lost", viewModel.state.value.error)
    }

    @Test
    fun `a previously saved dump is restored on start`() {
        repository.saveLastDump(context, g2Dump())
        val restored = CardViewModel(repository, context, ioDispatcher)
        assertNotNull(restored.state.value.card)
        assertTrue(restored.state.value.card!!.isV2)
        assertFalse(restored.state.value.isRestoring)
    }

    /** Amended G2 Application_Identification: 17 bytes, structure version {01 01}. */
    private fun g2Dump(): DriverCardDump {
        val b = ByteArrayOutputStream()
        b.write(0x00) // driver card
        b.write(0x01); b.write(0x01)
        b.write(12) // events per type
        b.write(24) // faults per type
        b.write(0x35); b.write(0xD0) // activity structure length
        b.write(0x00); b.write(200) // vehicle records
        b.write(0x00); b.write(112) // place records
        b.write(0x01); b.write(0x50) // GNSS AD records
        b.write(0x00); b.write(112) // specific conditions
        b.write(0x00); b.write(200) // vehicle unit records
        assertEquals(Layout.APP_ID_G2, b.size())
        return DriverCardDump.of(Layout.EF_APPLICATION_IDENTIFICATION to b.toByteArray())
    }
}
