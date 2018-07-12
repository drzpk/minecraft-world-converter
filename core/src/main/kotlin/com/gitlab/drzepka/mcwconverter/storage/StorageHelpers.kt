package com.gitlab.drzepka.mcwconverter.storage

import com.flowpowered.nbt.CompoundTag
import com.flowpowered.nbt.Tag

/**
 * Returns tag based on given path. Path consists of space-separated tag names. If no tag is found, a null value is returned.
 * Example: in order to obtain player tag, call this function on the root level tag: `rootTag.getTag("Data Player")`.
 */
@Suppress("UNCHECKED_CAST")
fun <T : Tag<out Any>> CompoundTag.getTag(path: String): T? {
    val parts = path.split(" ")
    var tag: Tag<out Any> = this
    for (part in parts) {
        try {
            tag = (tag as CompoundTag).value[part] as Tag<out Any>
        } catch (ignored: Exception) {
            return null
        }
    }
    return tag as T
}

/**
 * Returns tag value. Value must be a primitive type. If not tag is found, a null value is returned. Example: in order
 * to obtain game version, call this function on the root level tag: `rootTag.getTagValue("Data Version")`.
 *
 * **Note: ** this function is different from the [getTag]: it returns the tag value instead of tag, so you don't
 * have to manually call the [Tag.getValue] method.
 */
@Suppress("UNCHECKED_CAST")
fun <T> CompoundTag.getTagValue(path: String): T? {
    return getTag<Tag<Any>>(path)?.value as T?
}

/**
 * Checks whether given tag exist. For more information, check the [getTag] function.
 */
fun CompoundTag.hasTag(path: String): Boolean {
    return getTag<Tag<Any>>(path) != null
}