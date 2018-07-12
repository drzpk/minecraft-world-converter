package com.gitlab.drzepka.mcwconverter

import java.util.*

object Logger {

    fun i(message: String) {
        log("INFO", message)
    }

    fun w(message: String) {
        log("WARN", message)
    }

    fun e(message: String) {
        log("ERR", message)
    }

    /**
     * Displays progress in one line.
     */
    fun progress(finished: Int, total: Int, message: String) {
        val width = 20
        val progressWidth = Math.floor(finished.toDouble() / total * width + 0.5).toInt()
        val progressStr = "#".repeat(progressWidth) + " ".repeat(width - progressWidth)
        print("\t[$progressStr] $finished/$total, ${Math.floor(finished.toDouble() * 100 / total + 0.5)} $message")
    }

    /**
     * Asks user for something. [defaultChoice] will be returned if there is no input or it it wrong.
     */
    fun confirm(message: String, defaultChoice: Boolean = true): Boolean {
        val choices = "[" + (if (defaultChoice) "Y" else "y") + "/" + (if (!defaultChoice) "N" else "n") + "]"
        print("[?] $message $choices")

        val scanner = Scanner(System.`in`)
        val choice = scanner.next()
        scanner.close()

        return when {
            choice.toLowerCase() == "y" -> true
            choice.toLowerCase() == "n" -> false
            else -> defaultChoice
        }
    }

    private fun log(prefix: String, message: String) {
        println("[$prefix] $message")
    }
}