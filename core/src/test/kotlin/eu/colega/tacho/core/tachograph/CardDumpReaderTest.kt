package eu.colega.tacho.core.tachograph

import eu.colega.tacho.core.card.Apdu
import eu.colega.tacho.core.card.ApduResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the full card-download protocol (SELECT by AID, SELECT file,
 * chunked READ BINARY) against a scripted in-memory card.
 */
class CardDumpReaderTest {

    /** In-memory card: responds like a generation 2 driver card. */
    private class ScriptedCard : (ByteArray) -> ByteArray {
        val files = mutableMapOf<Int, ByteArray>()
        var selectedAid: ByteArray? = null
        var selectedFile: Int? = null
        var rejectAid = false

        override fun invoke(command: ByteArray): ByteArray {
            return when (command[1].toInt() and 0xFF) {
                Apdu.INS_SELECT -> handleSelect(command)
                Apdu.INS_READ_BINARY -> handleRead(command)
                else -> error("unexpected INS ${command[1]}")
            }
        }

        private fun handleSelect(command: ByteArray): ByteArray {
            val p1 = command[2].toInt() and 0xFF
            return if (p1 == 0x04) {
                // Select by AID.
                val aid = command.copyOfRange(5, 5 + (command[4].toInt() and 0xFF))
                selectedAid = aid
                selectedFile = null
                if (rejectAid) status(Apdu.SW_FILE_NOT_FOUND) else ok()
            } else {
                // Select by file identifier: 2-byte FID in the data.
                val fid = ((command[5].toInt() and 0xFF) shl 8) or (command[6].toInt() and 0xFF)
                if (files.containsKey(fid)) {
                    selectedFile = fid
                    ok()
                } else {
                    status(Apdu.SW_FILE_NOT_FOUND)
                }
            }
        }

        private fun handleRead(command: ByteArray): ByteArray {
            val fid = selectedFile ?: return status(Apdu.SW_NO_EF_SELECTED)
            val offset = ((command[2].toInt() and 0xFF) shl 8) or (command[3].toInt() and 0xFF)
            val requested = command[4].toInt() and 0xFF
            val data = files.getValue(fid)
            if (offset >= data.size) return status(Apdu.SW_EOF_REACHED)
            val end = minOf(offset + requested, data.size)
            val chunk = data.copyOfRange(offset, end)
            return chunk + status(Apdu.SW_OK)
        }

        private fun ok() = status(Apdu.SW_OK)

        private fun status(sw: Int) = byteArrayOf(
            ((sw shr 8) and 0xFF).toByte(),
            (sw and 0xFF).toByte(),
        )
    }

    private fun sampleFile(size: Int, seed: Int) = ByteArray(size) { ((it + seed) and 0xFF).toByte() }

    @Test
    fun `reads every elementary file in chunks of 255 bytes`() {
        val card = ScriptedCard().apply {
            files[Layout.EF_APPLICATION_IDENTIFICATION] = sampleFile(17, 1)
            files[Layout.EF_IDENTIFICATION] = sampleFile(143, 2)
            // 600 bytes forces three READ BINARY chunks (255, 255, 90).
            files[Layout.EF_EVENTS_DATA] = sampleFile(600, 3)
        }
        val dump = CardDumpReader(card).readDriverCard()

        // fileIds is sorted, not in read order.
        assertEquals(listOf(0x0501, 0x0502, 0x0520), dump.fileIds)
        assertTrue(dump.file(Layout.EF_APPLICATION_IDENTIFICATION)!!.contentEquals(sampleFile(17, 1)))
        assertTrue(dump.file(Layout.EF_IDENTIFICATION)!!.contentEquals(sampleFile(143, 2)))
        assertTrue(dump.file(Layout.EF_EVENTS_DATA)!!.contentEquals(sampleFile(600, 3)))
    }

    @Test
    fun `missing files are skipped without failing the dump`() {
        val card = ScriptedCard().apply {
            files[Layout.EF_APPLICATION_IDENTIFICATION] = sampleFile(17, 1)
            // Version 2-only file absent on a version 1 card.
        }
        val dump = CardDumpReader(card).readDriverCard()
        assertNull(dump.file(Layout.EF_APPLICATION_IDENTIFICATION_V2))
        assertEquals(1, dump.fileIds.size)
    }

    @Test
    fun `no tachograph application on the card fails loudly`() {
        val card = ScriptedCard().apply { rejectAid = true }
        val exception = runCatching { CardDumpReader(card).readDriverCard() }
            .exceptionOrNull()
        assertTrue(exception is IllegalStateException)
    }
}
