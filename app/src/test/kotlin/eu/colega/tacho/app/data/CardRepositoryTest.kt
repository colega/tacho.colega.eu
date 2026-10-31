package eu.colega.tacho.app.data

import eu.colega.tacho.core.card.TruncatedDataException
import eu.colega.tacho.core.tachograph.Layout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Tests the concatenated dump container: each EF is stored as
 * FID (2 bytes) + length (4 bytes) + payload.
 */
class CardRepositoryTest {

    private val repository = CardRepository()

    @Test
    fun `extracts every elementary file from a concatenated dump`() {
        val appId = ByteArray(17) { it.toByte() }
        val identification = ByteArray(143) { (it % 97).toByte() }
        val b = ByteArrayOutputStream()
        writeEntry(b, Layout.EF_APPLICATION_IDENTIFICATION, appId)
        writeEntry(b, Layout.EF_IDENTIFICATION, identification)

        val dump = repository.fromConcatenatedDump(b.toByteArray())
        assertTrue(
            dump.fileIds.containsAll(listOf(Layout.EF_APPLICATION_IDENTIFICATION, Layout.EF_IDENTIFICATION)),
        )
        assertEquals(appId.toList(), dump.file(Layout.EF_APPLICATION_IDENTIFICATION)!!.toList())
        assertEquals(identification.toList(), dump.file(Layout.EF_IDENTIFICATION)!!.toList())
    }

    @Test
    fun `truncated payload throws with the offending FID`() {
        val bytes = byteArrayOf(
            (Layout.EF_APPLICATION_IDENTIFICATION ushr 8).toByte(),
            Layout.EF_APPLICATION_IDENTIFICATION.toByte(),
            0x00, 0x00, 0x00, 0x10, // length 16, but no payload follows
        )
        val exception = assertThrows(TruncatedDataException::class.java) {
            repository.fromConcatenatedDump(bytes)
        }
        assertTrue(exception.message!!.contains("0501"))
    }

    @Test
    fun `empty dump is rejected`() {
        assertThrows(TruncatedDataException::class.java) {
            repository.fromConcatenatedDump(ByteArray(0))
        }
    }

    /** FID (2 bytes) + length (4 bytes) + payload, as the reader expects. */
    private fun writeEntry(b: ByteArrayOutputStream, fid: Int, payload: ByteArray) {
        b.write((fid ushr 8) and 0xFF)
        b.write(fid and 0xFF)
        b.write((payload.size ushr 24) and 0xFF)
        b.write((payload.size ushr 16) and 0xFF)
        b.write((payload.size ushr 8) and 0xFF)
        b.write(payload.size and 0xFF)
        b.write(payload)
    }
}
