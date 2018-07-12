package com.gitlab.drzepka.mcwconverter.level

import com.gitlab.drzepka.mcwconverter.PrintableException
import com.gitlab.drzepka.mcwconverter.storage.LevelStorage
import java.io.File
import java.io.PrintWriter

/**
 * This class loads two world and compares them, creating mapping that can be used to replace missing blocks or items.
 */
class LevelComparator(
        /** Directory of old world save from which conversion will be performed. */
        oldWorldDirectory: File,
        /** Directory of new world save that needs to be converted. */
        newWorldDirectory: File
) {
    init {
        if (!oldWorldDirectory.isDirectory)
            throw PrintableException("Old world save doesn't exist")
        if (!newWorldDirectory.isDirectory)
            throw PrintableException("New world save doesn't exist")

        val oldLevel = LevelStorage(oldWorldDirectory, "old world")
        val newLevel = LevelStorage(newWorldDirectory, "new world")

        if (oldLevel.versionId > newLevel.versionId)
            throw PrintableException("first given world save must have smaller version number " +
                    "(given: ${oldLevel.versionName} - ${newLevel.versionName})")
        if (oldLevel.versionId == newLevel.versionId)
            throw PrintableException("given world saves have exactly the same version (${oldLevel.versionName})")

        if (oldLevel.seed != newLevel.seed)
            throw PrintableException("given worlds have different seeds")
    }

    /**
     * Compares two levels and finds dirrefences between them.
     *
     * @param [output] writer analyze result will be written to
     */
    fun compare(output: PrintWriter) {

    }
}