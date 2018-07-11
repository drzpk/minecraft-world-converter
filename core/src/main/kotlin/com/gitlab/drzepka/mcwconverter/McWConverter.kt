package com.gitlab.drzepka.mcwconverter

import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException

class McWConverter {

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val options = Options()

            // Program modes
            options.addOption("a", "analyze", false,
                    "analyzes two world saves and finds differences between them")
            options.addOption("c", "convert", false,
                    "applies world conversion based on provided mapping")

            options.addOption("o", "output", true,
                    "file name to which analyze output will be saved. If isn't set, output is displayed in STDOUT (a)")
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

                val header = """An utility for converting minecraft world saves between game versions.
                    |Options denoted with mode shortcut "(a)" or "(c)" are applicable only to that mode.
                """.trimMargin()
                val footer = "For more information, read the wiki."

                formatter.printHelp("mcw_converter -a|-c [other options] old_world new_world", header, options, footer)
                return
            }

            if (!cmd.hasOption("a") && !cmd.hasOption("c")) {
                println("You must choose one of the modes: --analyze or --convert")
                return
            } else if (cmd.hasOption("a") && cmd.hasOption("c")) {
                println("You can select only one mode at at time")
                return
            }
        }
    }
}