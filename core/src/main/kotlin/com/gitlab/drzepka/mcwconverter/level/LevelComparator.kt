package com.gitlab.drzepka.mcwconverter.level

import com.flowpowered.nbt.CompoundTag
import com.gitlab.drzepka.mcwconverter.Logger
import com.gitlab.drzepka.mcwconverter.PrintableException
import com.gitlab.drzepka.mcwconverter.ResourceLocation
import com.gitlab.drzepka.mcwconverter.action.RenameBlockAction
import com.gitlab.drzepka.mcwconverter.storage.LevelStorage
import com.gitlab.drzepka.mcwconverter.storage.getTagValue
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
    private val oldLevel: LevelStorage
    private val newLevel: LevelStorage

    private val renameBlockActions = ArrayList<RenameBlockAction>()
    private val missingRenameBlockActions = ArrayList<RenameBlockAction>()

    private lateinit var logGenerator: LogGenerator

    init {
        if (!oldWorldDirectory.isDirectory)
            throw PrintableException("Old world save doesn't exist")
        if (!newWorldDirectory.isDirectory)
            throw PrintableException("New world save doesn't exist")

        oldLevel = LevelStorage(oldWorldDirectory, "old world")
        newLevel = LevelStorage(newWorldDirectory, "new world")

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
        logGenerator = LogGenerator(output)
        compareRegistryBlocks()
    }

    private fun compareRegistryBlocks() {
        val oldBlocks = oldLevel.rootTag.getTagValue<List<CompoundTag>>("FML Registries minecraft:blocks ids")!!
        val newBlocks = newLevel.rootTag.getTagValue<List<CompoundTag>>("FML Registries minecraft:blocks ids")!!

        for ((progress, oldBlock) in oldBlocks.withIndex()) {
            Logger.progress(progress + 1, oldBlocks.size, "comparing registry blocks")

            // Get block data
            val oldNameStr = oldBlock.value["K"]?.value as String? ?: ""
            if (oldNameStr.startsWith("minecraft:")) {
                // Minecraft known what it's doing, don't touch its blocks
                continue
            }

            val oldName = ResourceLocation(oldNameStr)
            val oldId = oldBlock.value["V"]?.value as Int? ?: 0

            var found = false
            for (newBlock in newBlocks) {
                val newName = newBlock.value["K"]?.value as String? ?: ""
                if (newName.startsWith("minecraft:")) {
                    // Minecraft known what it's doing, don't touch its blocks
                    continue
                }

                if (oldNameStr != newName && oldName.isSimilarTo(newName)) {
                    // Block name has changed, but was found. Id will stay the same, there is no point
                    // in changing it (user can change it manually though).
                    val action = RenameBlockAction()
                    action.oldName = oldName
                    action.newName = ResourceLocation(newName)
                    action.oldId = oldId
                    action.newId = oldId
                    renameBlockActions.add(action)

                    found = true
                    break
                } else if (oldNameStr == newName) {
                    // Block was found and didn't change (well, at least its name)
                    found = true
                    break
                }
            }

            if (!found) {
                // Current block existed in an old save, but wasn't found in a new one
                val action = RenameBlockAction()
                action.oldName = oldName
                action.newName = oldName
                action.oldId = oldId
                action.newId = oldId
                missingRenameBlockActions.add(action)
            }
        }

        logGenerator.generateSection(renameBlockActions, "doc_rename_block.txt")
        logGenerator.generateSection(missingRenameBlockActions, "doc_rename_block_missing.txt", true)
    }
}