package com.gitlab.drzepka.mcwconverter.level

import com.gitlab.drzepka.mcwconverter.action.BaseAction
import java.io.PrintWriter

/**
 * This class handles generation of analyze result logs.
 * @param writer writer to which log will be printed
 */
class LogGenerator(private val writer: PrintWriter) {

    /**
     * Generates new log section.
     * @param actions list of actions to be included in this section
     * @param headerName full name of the resource whose contents will serve as section header
     * @param commentOut whether to comment out each action from the list
     */
    fun generateSection(actions: List<BaseAction>, headerName: String, commentOut: Boolean = false) {
        // Sort actions
        val sortedActions = actions.sortedBy { it.sortableStr }

        val pack = javaClass.`package`.name.split(".").subList(0, 4).joinToString("/")
        val reader = javaClass.getResourceAsStream("/$pack/$headerName")
        writer.println()
        writer.println()
        reader.bufferedReader().forEachLine { writer.println("# $it") }
        sortedActions.forEach { writer.println("${if (commentOut) "# " else ""}${it.actionName} $it") }
    }
}