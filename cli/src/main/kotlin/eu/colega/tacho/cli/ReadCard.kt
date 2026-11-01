package eu.colega.tacho.cli

import eu.colega.tacho.core.card.ApduTransceiver
import eu.colega.tacho.core.tachograph.CardDumpReader
import eu.colega.tacho.core.tachograph.DriverCardParser
import java.io.File
import javax.smartcardio.Card
import javax.smartcardio.CardTerminal
import javax.smartcardio.TerminalFactory

/**
 * Reads a driver tachograph card straight through a PC/SC reader on the
 * desktop. Used for debugging the download against real hardware: every
 * APDU is printed, and the elementary files land in tmp/claude/dump/
 * for further analysis.
 */
fun main(args: Array<String>) {
    // The phone's CCID path may talk to the card in T=0 while desktop
    // PC/SC negotiates T=1; --t0 reproduces the phone behaviour here.
    val protocol = if ("--t0" in args) "T=0" else "*"
    val terminals = TerminalFactory.getDefault().terminals().list()
    if (terminals.isEmpty()) {
        System.err.println("No PC/SC reader found. Is the card reader plugged in?")
        return
    }
    println("Readers: " + terminals.joinToString { it.name })
    val terminal = terminals.firstOrNull { it.isCardPresent }
        ?: run {
            System.err.println("No card present in any reader. Insert the driver card and retry.")
            return
        }

    val card = terminal.connect(protocol)
    try {
        println("Reader: ${terminal.name}")
        println("Protocol: ${card.protocol} (requested $protocol)")
        println("ATR: ${card.atr.bytes.toHex()}")

        val reader = LoggingTransceiver(card)
        val dump = CardDumpReader(reader).readDriverCard()
        println()
        println("Download complete: ${dump.fileIds.size} elementary files")

        val dir = File("tmp/claude/dump").apply { mkdirs() }
        for (fid in dump.fileIds) {
            val bytes = dump.file(fid) ?: continue
            File(dir, "%04X.bin".format(fid)).writeBytes(bytes)
        }
        println("Raw files saved to ${dir.absolutePath}")

        val driver = DriverCardParser.parse(dump)
        println()
        println("Parsed: ${driver.holder.name.firstNames} ${driver.holder.name.surname}")
        println("Card number: ${driver.identification.cardNumber}")
        println("Validity: ${driver.identification.cardValidityBegin} .. ${driver.identification.cardExpiryDate}")
        println("Daily activity records: ${driver.activity.size}")
    } finally {
        card.disconnect(false)
    }
}

/** Logs every APDU exchange in hex for the APDU-level trace. */
private class LoggingTransceiver(private val card: Card) : ApduTransceiver {
    private val channel = card.basicChannel

    override fun transceive(command: ByteArray): ByteArray {
        val started = System.currentTimeMillis()
        val response = try {
            channel.transmit(javax.smartcardio.CommandAPDU(command)).bytes
        } catch (exception: Exception) {
            println("> ${command.toHex()}")
            println("! transceive failed: $exception")
            throw exception
        }
        println("> ${command.toHex()}")
        println("< ${response.toHex()}  (${System.currentTimeMillis() - started} ms)")
        return response
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
