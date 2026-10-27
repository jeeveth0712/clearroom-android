package com.clearroom.app

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** Backups hold the rooms and their day logs only (M12 audit): never MediaPipe's queued statistics. */
class BackupRulesTest {

    /** The rules files, read from the module's sources (unit tests run in the app module's folder). */
    private fun rules(name: String): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/$name")).documentElement

    private fun Element.children(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return List(nodes.length) { nodes.item(it) as Element }
    }

    private fun Element.includes(): Set<Pair<String, String>> = children("include").map { it.getAttribute("domain") to it.getAttribute("path") }.toSet()

    private val roomsAndLogs = setOf("file" to "rooms/", "file" to "logs/")

    @Test
    fun `cloud backup and phone-to-phone transfer include only the rooms and day logs`() {
        val rules = rules("data_extraction_rules.xml")
        listOf("cloud-backup", "device-transfer").forEach { section ->
            val sections = rules.children(section)
            assertEquals(1, sections.size, section)
            assertEquals(roomsAndLogs, sections.single().includes(), section)
        }
    }

    @Test
    fun `older Android backups follow the same rule`() {
        assertEquals(roomsAndLogs, rules("backup_rules.xml").includes())
    }
}
