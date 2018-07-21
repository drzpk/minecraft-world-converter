package com.gitlab.drzepka.mcwconverter.level

import com.gitlab.drzepka.mcwconverter.McWConverter
import com.gitlab.drzepka.mcwconverter.action.BaseAction
import com.gitlab.drzepka.mcwconverter.storage.LevelStorage
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * This class handles generation of analyze result logs.
 * @param writer writer to which log will be printed
 * @param oldLevel old level instance to generate file header
 * @param newLevel new level instance to generate file header
 */
class LogGenerator(private val writer: PrintWriter, oldLevel: LevelStorage, newLevel: LevelStorage) {

    init {
        val date = SimpleDateFormat("yyyy.MM.dd HH:mm")
        val stream = getResource("log_header.txt")
        val header = BufferedReader(InputStreamReader(stream))
                .readText()
                .replace("[version]", McWConverter.VERSION)
                .replace("[old_version]", oldLevel.versionName)
                .replace("[new_version]", newLevel.versionName)
                .replace("[seed]", oldLevel.seed.toString())
                .replace("[date]", date.format(Date()))
        writer.write(header + "\n\n")
    }

    /**
     * Generates new log section.
     * @param actions list of actions to be included in this section
     * @param headerName full name of the resource whose contents will serve as section header. If `null` header
     * name is given, new section won't be created.
     * @param commentOut whether to comment out each action from the list
     */
    fun generateSection(actions: List<BaseAction>, headerName: String?, commentOut: Boolean = false) {
        // Sort actions
        val sortedActions = actions.sortedBy { it.sortableStr }

        if (headerName != null) {
            val reader = getResource(headerName)
            writer.println()
            writer.println()
            reader.bufferedReader().forEachLine { writer.println("# $it") }
        }
        sortedActions.forEach { writer.println("${if (commentOut) "# " else ""}${it.actionName} $it") }
    }

    private fun getResource(name: String): InputStream {
        val pack = javaClass.`package`.name.split(".").subList(0, 4).joinToString("/")
        return javaClass.getResourceAsStream("/$pack/$name")
    }
}