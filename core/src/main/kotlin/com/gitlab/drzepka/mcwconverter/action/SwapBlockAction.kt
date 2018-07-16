package com.gitlab.drzepka.mcwconverter.action

import com.gitlab.drzepka.mcwconverter.PrintableException
import java.util.*

/**
 * Action used to replace blocks in world.
 *
 * It should be used to replace blocks that have changed completly between versions, e.g. a block that previously had
 * its own ID now shares it with other blocks and is distinguished only by metadata. If only ID has changed, you
 * should use the [RenameBlockAction] instead. Note, that this action has higher priority that the [RenameBlockAction],
 * so if both actions replace the same block id, this one will be executed and the other will be ignored.
 *
 * Example:
 * ```
 * SwapBlock oldid:oldmeta -> newid:newmeat (optional hint)
 * SwapBlock 1432:3 -> 122:0
 * SwapBlock 112:7 -> 879:2 (modid:old_name -> modid:new_name)
 * ```
 * You must provide both id and meta of block from an old and from a new save accordingly. Parentheses are provided
 * as a hint by the converter and are ignored.
 */
class SwapBlockAction : BaseAction() {

    override val actionName = "SwapBlock"
    override var sortableStr = ""
        get() = if (field.isEmpty()) toString() else field

    /** Id of block in an old world save. */
    var oldId: Short = 0
    /** Metadata of block in an old world save. */
    var oldMeta: Short = 0
    /** Id of block in a new world save. */
    var newId: Short = 0
    /** Metadata of block in a new world save. */
    var newMeta: Short = 0

    /** List of mappings (block id -> block registry name) that will be used to generate a hint. */
    var blockMappings: List<Pair<Int, String>>? = null

    override fun toString(): String {
        var str = "$oldId:$oldMeta -> $newId:$newMeta"
        if (blockMappings != null) {
            // Search actions for a hint
            var oldMapping: String? = null
            var newMapping: String? = null

            for (entry in blockMappings!!) {
                if (oldMapping == null && entry.first.toShort() == this.oldId) {
                    oldMapping = entry.second
                    if (newMapping != null)
                        break
                }
                if (newMapping == null && entry.first.toShort() == this.newId) {
                    newMapping = entry.second
                    if (oldMapping != null)
                        break
                }
            }

            if (oldMapping != null)
                str += " ($oldMapping -> " + (newMapping ?: "unknown") + ")"
        }

        sortableStr = str
        return str
    }

    override fun parse(source: String) {
        val parts = source.split(" ")
        try {
            val oldData = parts[0].split(':')
            oldId = oldData[0].toShort()
            oldMeta = oldData[1].toShort()

            val newData = parts[2].split(':')
            newId = newData[0].toShort()
            newMeta = newData[1].toShort()
        } catch (ignored: Exception) {
            throw PrintableException("syntax error")
        }

        if (oldId < 0 || oldId > 4095)
            throw PrintableException("old block is should be in range [0, 4096)")
        if (newId < 0 || newId > 4095)
            throw PrintableException("new block is should be in range [0, 4096)")
        if (oldMeta < 0 || oldMeta > 15)
            throw PrintableException("old block metadata should be in range [0, 16)")
        if (newMeta < 0 || newMeta > 15)
            throw PrintableException("new block metadata should be in range [0, 16)")
    }

    override fun equals(other: Any?): Boolean {
       if (other !is SwapBlockAction)
            return false

        return oldId == other.oldId && oldMeta == other.oldMeta && newId == other.newId && newMeta == other.newMeta
    }

    override fun hashCode(): Int = Objects.hash(oldId, oldMeta, newId, newMeta)
}