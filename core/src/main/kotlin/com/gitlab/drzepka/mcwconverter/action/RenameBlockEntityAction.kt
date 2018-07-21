package com.gitlab.drzepka.mcwconverter.action

import com.gitlab.drzepka.mcwconverter.PrintableException
import com.gitlab.drzepka.mcwconverter.ResourceLocation
import java.util.*

/**
 * Action used to rename block entities (formerly known as tile entities) in world.
 *
 * Identifiers of blcok entities may vary between versions. This action tries to rename them, so they will be
 * recognized by mods from newer version. It works on regular block entities as well as on the ones using the Forge
 * multipart framework.
 *
 * Example:
 * ```
 * RenameBlockEntity [multipart] <old name> -> <new name>
 * RenameBlockEntity [multipart] <old name> -> [multipart] <new name>
 * RenameBlockEntity modid:oldEntityId -> modid:new_entity_id
 * ```
 * If you type "multipart" before a name means that block entity is a Forge multipart component. You can remove the
 * "multipart" prefix from the right side and block entity will become a regular block entity. (Make sure you know
 * what you're doing).
 */
class RenameBlockEntityAction : BaseAction() {

    override val actionName = "RenameBlockEntity"
    override var sortableStr = ""
        get() = if (field.isEmpty()) toString() else field

    /** Old block entity name. */
    var oldName = ResourceLocation()
    /** Whether old block entity uses Forge multipart */
    var isOldMultipart = false
    /** New block entity name. */
    var newName = ResourceLocation()
    /** Whether new block entity use Forge multipart */
    var isNewMultipart = false

    override fun toString(): String {
        val oldMulti = if (isOldMultipart) "multipart " else ""
        val newMulti = if (isNewMultipart) "multipart " else ""
        sortableStr = "$oldMulti$oldName -> $newMulti$newName"
        return sortableStr
    }

    override fun parse(source: String): BaseAction {
        val parts = source.split("->").map { it.trim() }
        val action = RenameBlockEntityAction()

        try {
            action.isOldMultipart = parts[0].contains("multipart", true)
            action.oldName = ResourceLocation(if (isOldMultipart) parts[0].trim().split(' ')[1] else parts[0])
            action.isNewMultipart = parts[1].contains("multipart", true)
            action.newName = ResourceLocation(if (isNewMultipart) parts[1].trim().split(' ')[1] else parts[1])
        } catch (ignored: Exception) {
            throw PrintableException("syntax error")
        }

        if (!action.isOldMultipart && action.isNewMultipart)
            throw PrintableException("non-Forge multipart block entity cannot be converted to Forge multipart")

        return action
    }

    override fun equals(other: Any?): Boolean {
        if (other !is RenameBlockEntityAction)
            return false

        return other.oldName == oldName && other.newName == newName
    }

    override fun hashCode(): Int = Objects.hash(oldName, newName)
}