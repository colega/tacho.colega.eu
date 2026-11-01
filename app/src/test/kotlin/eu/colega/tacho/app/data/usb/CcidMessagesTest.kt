package eu.colega.tacho.app.data.usb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Byte-level tests for the CCID frame builder and parser. */
class CcidMessagesTest {

    @Test
    fun `power on frame is a clean 10 byte header with automatic voltage`() {
        val frame = CcidMessages.powerOn(slot = 0, sequence = 7)
        assertEquals(CcidMessages.HEADER_SIZE, frame.size)
        assertEquals(CcidMessages.PC_TO_RDR_ICC_POWER_ON, frame[0].toInt() and 0xFF)
        // dwLength = 0, little-endian: no payload beyond the header.
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), frame.copyOfRange(1, 5))
        assertEquals(0, frame[5].toInt()) // slot
        assertEquals(7, frame[6].toInt()) // sequence
        // Automatic voltage/protocol selection in the bStatus/bError octets.
        assertEquals(0, frame[7].toInt())
        assertEquals(CcidMessages.PROTOCOL_AUTO, frame[8].toInt())
        assertEquals(0, frame[9].toInt())
    }

    @Test
    fun `xfr block carries the apdu as payload`() {
        val apdu = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x0C, 0x06, 0xFF.toByte(), 0x54, 0x41, 0x43, 0x48, 0x4F)
        val frame = CcidMessages.xfrBlock(apdu, slot = 0, sequence = 42)
        assertEquals(CcidMessages.PC_TO_RDR_XFR_BLOCK, frame[0].toInt() and 0xFF)
        assertEquals(42, frame[6].toInt())
        assertEquals(apdu.size, frame[1].toInt() and 0xFF)
        assertArrayEquals(apdu, frame.copyOfRange(10, 10 + apdu.size))
    }

    @Test
    fun `parses a data block response and reports status`() {
        val payload = byteArrayOf(0x61, 0x05)
        val frame = ByteArray(CcidMessages.HEADER_SIZE + payload.size)
        frame[0] = CcidMessages.RDR_TO_PC_DATA_BLOCK.toByte()
        frame[1] = payload.size.toByte() // dwLength LE
        frame[6] = 3 // sequence
        frame[7] = 0 // status: ok, ICC active
        payload.copyInto(frame, CcidMessages.HEADER_SIZE)

        val response = CcidMessages.parse(frame)
        assertEquals(CcidMessages.RDR_TO_PC_DATA_BLOCK, response.messageType)
        assertEquals(3, response.sequence)
        assertTrue(response.statusOk)
        assertArrayEquals(payload, response.payload)
    }

    @Test
    fun `reports a reader error status as not ok`() {
        val frame = ByteArray(CcidMessages.HEADER_SIZE)
        frame[0] = CcidMessages.RDR_TO_PC_SLOT_STATUS.toByte()
        frame[7] = 0x42 // command failed, ICC present but inactive
        val response = CcidMessages.parse(frame)
        assertFalse(response.statusOk)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `truncated frames are rejected`() {
        CcidMessages.parse(ByteArray(CcidMessages.HEADER_SIZE - 1))
    }
}
