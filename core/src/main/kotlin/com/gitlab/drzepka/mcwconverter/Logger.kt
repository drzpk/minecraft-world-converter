package com.gitlab.drzepka.mcwconverter

import java.util.*

object Logger {

    fun i(message: String, thread: Boolean = false) {
        log("INFO", message, null, thread)
    }

    fun w(message: String, thread: Boolean = false) {
        log("WARN", message, null, thread)
    }

    fun e(message: String, exception: Throwable? = null, thread: Boolean = false) {
        log("ERR", message, exception, thread)
    }

    /**
     * Displays progress in one line.
     */
    fun progress(finished: Int, total: Int, message: String) {
        val width = 20
        val progressWidth = Math.floor(finished.toDouble() / total * width + 0.5).toInt()
        val progressStr = "#".repeat(progressWidth) + " ".repeat(width - progressWidth)
        print("\r[$progressStr] $finished/$total, ${Math.floor(finished.toDouble() * 100 / total + 0.5).toInt()}% $message")
    }

    /**
     * Asks user for something. [defaultChoice] will be returned if there is no input or it it wrong.
     */
    fun confirm(message: String, defaultChoice: Boolean = true): Boolean {
        val choices = "[" + (if (defaultChoice) "Y" else "y") + "/" + (if (!defaultChoice) "N" else "n") + "]"
        print("[?] $message $choices")

        val scanner = Scanner(System.`in`)
        val choice = scanner.nextLine()

        return when {
            choice.toLowerCase() == "y" -> true
            choice.toLowerCase() == "n" -> false
            else -> defaultChoice
        }
    }

    private fun log(prefix: String, message: String, exception: Throwable?, thread: Boolean) {
        synchronized(this.javaClass) {
            if (thread)
                McWConverter.PRINT_LOCK.lock()
            println("[$prefix] $message")
            exception?.printStackTrace()
            if (thread) {
                System.out.flush()
                McWConverter.PRINT_LOCK.unlock()
            }
        }
    }
}