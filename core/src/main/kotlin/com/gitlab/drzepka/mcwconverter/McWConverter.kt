package com.gitlab.drzepka.mcwconverter

import com.gitlab.drzepka.mcwconverter.level.LevelComparator
import org.apache.commons.cli.*
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.util.concurrent.locks.ReentrantLock
import kotlin.system.exitProcess

class McWConverter {

    companion object {

        /** Use this lock to gain exclusive access to [System.out] stream. */
        val PRINT_LOCK = ReentrantLock()

        private const val VERSION = "1.0"

        @JvmStatic
        fun main(args: Array<String>) {
            val options = Options()

            // Program modes
            options.addOption("a", "analyze", false,
                    "analyzes two world saves and finds differences between them")
            options.addOption("c", "convert", false,
                    "applies world conversion based on provided mapping")

            options.addOption("o", "output", true,
                    "file name to which analyze output will be saved (a)")
            options.addOption("i", "input", true,
                    "file name of the analyze output that will be used for conversion {c)")

            options.addOption("h", "help", false, "display this help message")

            val parser = DefaultParser()
            val cmd = try {
                parser.parse(options, args, false)!!
            } catch (exception: ParseException) {
                println(exception.message)
                return
            }

            if (cmd.hasOption("h")) {
                val formatter = HelpFormatter()
                formatter.optionComparator = null

                val header = """Minecraft World Converter (version $VERSION)
                    |An utility for converting minecraft world saves between game versions.
                    |Options denoted with mode shortcut "(a)" or "(c)" are applicable only to that mode.
                """.trimMargin()
                val footer = "For more information, read the wiki."

                formatter.printHelp("mcw_converter -a|-c [other options] [old_world] new_world", header, options, footer)
                return
            }

            if (!cmd.hasOption("a") && !cmd.hasOption("c")) {
                println("You must choose one of the modes: --analyze or --convert")
                return
            } else if (cmd.hasOption("a") && cmd.hasOption("c")) {
                println("You can select only one mode at at time")
                return
            }

            try {
                execute(cmd)
            } catch (exception: PrintableException) {
                println(exception.message)
                return
            }
        }

        private fun execute(cmd: CommandLine) {
            if (cmd.hasOption("a")) {
                // Compare two worlds
                if (cmd.args.size != 2) {
                    Logger.e("You must provide exactly 2 world save locations")
                    exitProcess(-1)
                }

                // Get output file name
                val fileName = if (!cmd.hasOption("o")) {
                    if (Logger.confirm("You haven't given output file name. Analyze results will be saved" +
                                    "to a file named 'analyze_results.log'. Do you want to continue?"))
                        "analyze_results.log"
                    else {
                        Logger.i("program aborted by user")
                        exitProcess(-1)
                    }
                } else
                    cmd.getOptionValue("o")!!

                val resultFile = File(fileName)
                if (resultFile.isDirectory) {
                    Logger.e("Given output file path points to a directory")
                    exitProcess(-1)
                }

                if (resultFile.isFile && !Logger.confirm("Analyze results file already exists. Do you want to overwrite it?")) {
                    Logger.i("program aborted by user")
                    exitProcess(-1)
                }

                // Prepare analyze output
                val output = PrintWriter(FileOutputStream(resultFile, false))

                // All file checkings are done elsewhere
                val oldDir = File(cmd.args[0])
                val newDir = File(cmd.args[1])
                compareWorlds(oldDir, newDir, output)
                output.close()
            } else {
                // Convert given world
                if (cmd.args.size != 1) {
                    Logger.e("You must provide exactly 1 world save location")
                    exitProcess(-1)
                }

                convertWorld(File(cmd.args[0]))
            }
        }

        private fun compareWorlds(oldSaveDir: File, newSaveDir: File, output: PrintWriter) {
            val comparator = LevelComparator(oldSaveDir, newSaveDir)
            comparator.compare(output)
        }

        private fun convertWorld(worldSaveDir: File) {

        }
    }
}