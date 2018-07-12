package com.gitlab.drzepka.mcwconverter.action

import com.gitlab.drzepka.mcwconverter.PrintableException
import com.gitlab.drzepka.mcwconverter.ResourceLocation

/**
 * Action used to rename blocks in a Forge registry.
 *
 * When new version of game or mod comes out, blocks' registry names can change, which results in vanishing some
 * of the blocks. This action converts world save so it contains valid block registry names. Blocks IDs can also
 * be reassigned. **Warning:** block meta isn't supported here - it is handled unsing another action.
 *
 * Example:
 * ```
 * RenameBlock <old block registry name> <old block id> -> <new block registry name> <new block id>
 * RenameBlock modid:blockName 433 -> modid:block_name 182
 * ```
 */
class RenameBlockAction : BaseAction() {

    override val actionName = "RenameBlock"
    override val sortableStr: String
        get() = oldName.fullName

    /** Old block name. */
    var oldName = ResourceLocation()
    /** Old block id. */
    var oldId = 0
    /** New block name. */
    var newName = ResourceLocation()
    /** New block id. */
    var newId = 0

    override fun toString(): String = "$oldName $oldId -> $newName $newId"

    override fun parse(source: String) {
        val parts = source.trim().split(" ")
        if (parts.size != 5 || parts[2] != "->")
            throw PrintableException("wrong action format")

        oldName = ResourceLocation(parts[0])
        oldId = parts[1].toIntOrNull() ?: 0
        newName = ResourceLocation(parts[3])
        newId = parts[4].toIntOrNull() ?: 0
    }
}