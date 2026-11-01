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
    // --demo writes a synthetic card dump (fictional driver, no personal
    // data) for public screenshots and emulator testing.
    if ("--analyze" in args) {
        val argPath = args.getOrNull(args.indexOf("--analyze") + 1) ?: "testdata/card/demo.tcd"
        val path = if (File(argPath).isAbsolute) argPath else File(repoRoot(), argPath).absolutePath
        val dump = eu.colega.tacho.core.tachograph.TcdContainer.fromBytes(File(path).readBytes())
        val card = eu.colega.tacho.core.tachograph.DriverCardParser.parse(dump)
        val analysis = eu.colega.tacho.core.analysis.ActivityAnalysis.analyze(card.activity)
        println("days: " + card.activity.size)
        analysis.violations.forEach { println("" + it.date + " " + it.type + " " + it.description + " (+" + it.excessMinutes + "m)") }
        if (analysis.violations.isEmpty()) println("no violations")
        return
    }
    if ("--demo" in args) {
        val root = repoRoot()
        val dir = File(root, "testdata/card").apply { mkdirs() }
        File(dir, "demo.tcd").writeBytes(DemoCard.container())
        println("Demo dump written to ${File(dir, "demo.tcd").absolutePath}")
        return
    }
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

        // Local, git-ignored test asset: the portable container for the
        // emulator import plus the raw elementary files for byte-level work.
        val dir = File(repoRoot(), "testdata/card").apply { mkdirs() }
        File(dir, "card.tcd").writeBytes(eu.colega.tacho.core.tachograph.TcdContainer.toBytes(dump))
        for (fid in dump.fileIds) {
            val bytes = dump.file(fid) ?: continue
            File(dir, "%04X.bin".format(fid)).writeBytes(bytes)
        }
        println("Dump saved to ${dir.absolutePath} (card.tcd + 23 EF files)")
        println("Emulator: adb push ${dir.absolutePath}/card.tcd /sdcard/Download/")

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

/** The repo root (the directory holding the settings file); Gradle runs
 *  this tool with the module directory as cwd, so walk up from there. */
private fun repoRoot(): File =
    generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").exists() }
        ?: File(System.getProperty("user.dir"))

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
