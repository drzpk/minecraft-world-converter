package com.gitlab.drzepka.mcwconverter.action

/**
 * Action used to rename items in a Forge registry.
 *
 * It works exactly as the [RenameBlockAction], but handles items instead of blocks.
 *
 * Example:
 * ```
 * RenameItem <old item registry name> <old item id> -> <new item registry name> <new item id>
 * RenameItem modid:itemName 433 -> modid:item_name 182
 * ```
 */
class RenameItemAction : RenameBlockAction() {
    override val actionName = "RenameItem"
}