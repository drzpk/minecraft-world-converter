package com.gitlab.drzepka.mcwconverter.storage

import com.flowpowered.nbt.CompoundTag
import com.flowpowered.nbt.stream.NBTInputStream
import com.gitlab.drzepka.mcwconverter.PrintableException
import java.io.File
import java.io.FileInputStream

/**
 * Used to load and manage minecraft world save.
 */
class LevelStorage(
        /** World save directory. */
        levelDirectory: File,
        /** World name used when displaying errors. */
        levelCodeName: String
) {

    /** Returns version of this world save. Higher number means more recent game version. */
    val versionId: Int
        get() = rootTag.getTagValue("Data Version Id")!!
    /** Return string version of this world save. (1.10.2, 1.12.2 etc.) */
    val versionName: String
        get() = rootTag.getTagValue("Data Version Name")!!
    /** Get seed associated with this level. */
    val seed: Long
        get() = rootTag.getTagValue("Data RandomSeed")!!

    /** Main tag of thie level (level.dat). */
    val rootTag: CompoundTag

    init {
        val levelFile = File(levelDirectory, "level.dat")
        if (!levelFile.exists() || !levelFile.isFile)
            throw PrintableException("no level.dat file found")

        val stream = NBTInputStream(FileInputStream(levelFile))
        rootTag = stream.readTag() as CompoundTag
        stream.close()

        // Check whether this level is modded
        if (!rootTag.hasTag("FML"))
            throw PrintableException("your $levelCodeName is not modded. Only modded worlds are supported")
    }
}