package com.gitlab.drzepka.mcwconverter.level

import com.flowpowered.nbt.CompoundTag
import com.gitlab.drzepka.mcwconverter.Logger
import com.gitlab.drzepka.mcwconverter.PrintableException
import com.gitlab.drzepka.mcwconverter.ResourceLocation
import com.gitlab.drzepka.mcwconverter.action.RenameBlockAction
import com.gitlab.drzepka.mcwconverter.action.RenameBlockEntityAction
import com.gitlab.drzepka.mcwconverter.action.RenameItemAction
import com.gitlab.drzepka.mcwconverter.action.SwapBlockAction
import com.gitlab.drzepka.mcwconverter.storage.Chunk
import com.gitlab.drzepka.mcwconverter.storage.LevelStorage
import com.gitlab.drzepka.mcwconverter.storage.getTagValue
import com.gitlab.drzepka.mcwconverter.util.CooldownLock
import java.io.File
import java.io.PrintWriter
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.HashSet

/**
 * This class loads two world and compares them, creating mapping that can be used to replace missing blocks or items.
 */
class LevelComparator(
        /** Directory of old world save from which conversion will be performed. */
        oldWorldDirectory: File,
        /** Directory of new world save that needs to be converted. */
        newWorldDirectory: File,
        /** Whether the strict comparison mode should be enabled. */
        private val strictMode: Boolean = false
) {
    private val oldLevel: LevelStorage
    private val newLevel: LevelStorage
    private lateinit var logGenerator: LogGenerator
    private var totalChunks = 0
    private val progress = AtomicInteger(0)

    private lateinit var renameBlockActions: ArrayList<RenameBlockAction>

    private val threadPool = Executors.newFixedThreadPool(AVAILABLE_CPUS)
    private val queue = ArrayBlockingQueue<Pair<Chunk, Chunk>>(AVAILABLE_CPUS * 2)
    private val cooldownLock = CooldownLock(500)

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

        if (strictMode) {
            Logger.w("Strict mode is enabled. Blocks' counterparts from new world save are expected to be " +
                    "in the same locations as the original ones.")
            Logger.w("This may generate a lot of redundant and potentially dangerous actions. Use with caution!")
        }
    }

    /**
     * Compares two levels and finds dirrefences between them.
     *
     * @param [output] writer analyze result will be written to
     */
    fun compare(output: PrintWriter) {
        logGenerator = LogGenerator(output, oldLevel, newLevel)
        compareRegistry("blocks", true)
        compareRegistry("items", false)

        val oldRegions = oldLevel.regions()

        // Compute amount of work to do
        Logger.i("Gathering chunk info")
        totalChunks = oldRegions.sumBy { it.countChunks() }

        val threads = (0 until AVAILABLE_CPUS).map { threadPool.submit(ChunkComparator()) }

        for (oldRegion in oldRegions) {
            val newRegion = newLevel.getRegion(oldRegion.regionX, oldRegion.regionZ) ?: continue
            val oldChunks = oldRegion.readChunks()
            val newChunks = newRegion.readChunks()

            for (i in 0 until 1024) {
                if (oldChunks[i] == null || newChunks[i] == null)
                    continue

                queue.put(Pair(oldChunks[i]!!, newChunks[i]!!))
            }
        }

        threadPool.shutdown()
        threadPool.awaitTermination(1, TimeUnit.DAYS)
        Logger.progress(totalChunks, totalChunks, "Processing chunks\n")

        Logger.i("Processing results")
        val swapBlockActions = HashSet<SwapBlockAction>()
        val renameBlockEntityActions = HashSet<RenameBlockEntityAction>()
        val missingRenameBlockEntityActions = HashSet<RenameBlockEntityAction>()

        // Gather all results from threads
        for (thread in threads) {
            val result = thread.get()
            if (result != null) {
                swapBlockActions.addAll(result.swapBlockActions)
                renameBlockEntityActions.addAll(result.renameBlockEntityActions)
                missingRenameBlockEntityActions.addAll(result.missingRenameBlockEntityActions)
            }
        }

        // Prepare list for hint generation in SwapBlockActions
        val blockRegistry = oldLevel.rootTag.getTagValue<List<CompoundTag>>("FML Registries minecraft:blocks ids")!!
        val blockMappings = ArrayList<Pair<Int, String>>(blockRegistry.size)
        for (entry in blockRegistry) {
            val mapping = Pair(entry.getTagValue<Int>("V")!!, entry.getTagValue<String>("K")!!)
            blockMappings.add(mapping)
        }

        // Remove all swap actions that would modify a minecraft block
        val minecraftIds = blockMappings.filter { it.second.startsWith("minecraft:") }.map { it.first }
        swapBlockActions.removeAll { minecraftIds.contains(it.oldId.toInt()) }

        // Append the SwapBlock section to a log file
        swapBlockActions.forEach { it.blockMappings = blockMappings }
        val mutableSwapBlockActions = swapBlockActions.toMutableList()
        mutableSwapBlockActions.sortBy { it.sortableStr }
        logGenerator.generateSection(swapBlockActions.toMutableList().sortedBy { it.sortableStr }, "doc_swap_blocks.txt", true)

        // Make sure that block entity marked as missing wasn't found elsewhere in the new world save
        val iterator = missingRenameBlockEntityActions.iterator()
        while (iterator.hasNext()) {
            val missingAction = iterator.next()
            for (plainAction in renameBlockEntityActions) {
                if (plainAction.oldName == missingAction.oldName) {
                    // Action mapping was found elsewhere
                    iterator.remove()
                    break
                }
            }
        }

        // Append the RenameBlockEntity actions to a log file
        logGenerator.generateSection(renameBlockEntityActions.toMutableList().sortedBy { it.sortableStr }, "doc_rename_block_entities.txt")
        logGenerator.generateSection(missingRenameBlockEntityActions.toMutableList().sortedBy { it.sortableStr }, null, true)
    }

    private fun compareRegistry(what: String, blocks: Boolean) {
        val oldList = oldLevel.rootTag.getTagValue<List<CompoundTag>>("FML Registries minecraft:$what ids")!!
        val newList = newLevel.rootTag.getTagValue<List<CompoundTag>>("FML Registries minecraft:$what ids")!!
        val renameActions = ArrayList<RenameBlockAction>()
        val missingRenameActions = ArrayList<RenameBlockAction>()

        for ((progress, oldBlock) in oldList.withIndex()) {
            Logger.progress(progress + 1, oldList.size, "comparing registry $what")

            // Get block/item data
            val oldNameStr = oldBlock.value["K"]?.value as String? ?: ""
            if (oldNameStr.startsWith("minecraft:")) {
                // Minecraft known what it's doing, don't touch its entries
                continue
            }

            val oldName = ResourceLocation(oldNameStr)
            val oldId = oldBlock.value["V"]?.value as Int? ?: 0

            var found = false
            for (newBlock in newList) {
                val newName = newBlock.value["K"]?.value as String? ?: ""
                if (newName.startsWith("minecraft:")) {
                    // Minecraft known what it's doing, don't touch its entries
                    continue
                }

                if (oldNameStr != newName && oldName.isSimilarTo(newName)) {
                    // Block/item name has changed, but was found. Id will stay the same, there is no point
                    // in changing it (user can change it manually though).
                    val action = if (blocks) RenameBlockAction() else RenameItemAction()
                    action.oldName = oldName
                    action.newName = ResourceLocation(newName)
                    action.oldId = oldId
                    action.newId = oldId
                    renameActions.add(action)

                    found = true
                    break
                } else if (oldNameStr == newName) {
                    // Block/item was found and didn't change (well, at least its name)
                    found = true
                    break
                }
            }

            if (!found) {
                // Current block/item existed in an old save, but wasn't found in a new one
                val action = if (blocks) RenameBlockAction() else RenameItemAction()
                action.oldName = oldName
                action.newName = oldName
                action.oldId = oldId
                action.newId = oldId
                missingRenameActions.add(action)
            }
        }

        if (blocks)
            renameBlockActions = renameActions

        logGenerator.generateSection(renameActions, "doc_rename_$what.txt")
        logGenerator.generateSection(missingRenameActions, null, true)
        println()
    }

    /**
     * Compares chunk from old world save with chunk from new world save and finds differences between them.
     */
    private inner class ChunkComparator : Callable<ChunkComparatorResult?> {

        private val renameBlockEntityActions = HashSet<RenameBlockEntityAction>()
        private val missingRenameBlockEntityActions = HashSet<RenameBlockEntityAction>()
        private val processedBlockEntities = HashSet<String>()

        override fun call(): ChunkComparatorResult? {
            return try {
                doWork()
            } catch (exception: Throwable) {
                Logger.e("Unhandled exception in chunk comparator!", exception, true)
                null
            }
        }

        private fun doWork(): ChunkComparatorResult {
            val swapBlockActions = ArrayList<SwapBlockAction>()

            while (true) {
                val pair = queue.poll(250, TimeUnit.MILLISECONDS) ?: if (threadPool.isShutdown)
                    break
                else
                    continue

                val oldChunk = pair.first
                val newChunk = pair.second

                // Iterate over every block in chunk
                for (section in 0 until 16) {
                    if (!oldChunk.hasSection(section) || !newChunk.hasSection(section))
                        continue

                    for (y in 0 until 16) {
                        for (z in 0 until 16) {
                            for (x in 0 until 16) {
                                val realY = section * 16 + y
                                val flag = if (!strictMode)
                                    oldChunk.getBlockId(x, realY, z) != 0.toShort() && newChunk.getBlockId(x, realY, z) == 0.toShort()
                                else {
                                    val oldId = oldChunk.getBlockId(x, realY, z)
                                    val newId = oldChunk.getBlockId(x, realY, z)
                                    oldId != 0.toShort() && newId != 0.toShort() && oldId != newId
                                }

                                if (flag) {
                                    // Block vanished in a new world save
                                    val action = SwapBlockAction()
                                    action.oldId = oldChunk.getBlockId(x, realY, z)
                                    action.oldMeta = oldChunk.getBlockMeta(x, realY, z)
                                    swapBlockActions.add(action)
                                }
                            }
                        }
                    }
                }

                // Compare block entities
                val oldBlockEntities = oldChunk.rootTag.getTagValue<List<CompoundTag>>("Level TileEntities")
                val newBlockEntities = newChunk.rootTag.getTagValue<List<CompoundTag>>("Level TileEntities")
                if (oldBlockEntities?.isNotEmpty() == true && newBlockEntities?.isNotEmpty() == true)
                    compareBlockEntities(oldBlockEntities, newBlockEntities)

                updateProgress()
            }

            return ChunkComparatorResult(
                    swapBlockActions,
                    renameBlockEntityActions,
                    missingRenameBlockEntityActions)
        }

        private fun compareBlockEntities(oldTiles: List<CompoundTag>, newTiles: List<CompoundTag>) {
            for (old in oldTiles) {
                val oldNameList = getRealBlockEntityNames(old)
                if (oldNameList.isEmpty())
                    continue

                // One block entity can hold several other entities (Forge multipart)
                for ((oldNameStr, isOldMultipart) in oldNameList) {
                    if (processedBlockEntities.contains(oldNameStr)) {
                        // Block entity with this id was already processed and its mapping was found
                        continue
                    }

                    val oldName by lazy { ResourceLocation(oldNameStr) }
                    val oldX by lazy { old.getTagValue<Int>("x") }
                    val oldY by lazy { old.getTagValue<Int>("y") }
                    val oldZ by lazy { old.getTagValue<Int>("z") }

                    var mappingFound = false
                    for (new in newTiles) {
                        val newNameList = getRealBlockEntityNames(new)
                        if (newNameList.isEmpty())
                            continue

                        // One block entity can hold several other entities (Forge multipart)
                        for ((newNameStr, isNewMutipart) in newNameList) {
                            // When strict comparison mode is enabled, only block entity coordinates are checked
                            // and names are simply ignored. That's because block entities are expected to be in
                            // the same position as they were in old world save.

                            if (oldNameStr == newNameStr) {
                                // New block entity matches the old one, nothing needs to be done
                                continue
                            }

                            // Block entities don't match
                            if ((!oldName.isSimilarTo(newNameStr) && !strictMode)
                                    || oldX != new.getTagValue<Int>("x")
                                    || oldY != new.getTagValue<Int>("y")
                                    || oldZ != new.getTagValue<Int>("z"))
                                continue

                            // Found mapping old tile -> new tile
                            mappingFound = true
                            val action = RenameBlockEntityAction()
                            action.oldName = oldName
                            action.newName = ResourceLocation(newNameStr)
                            action.isOldMultipart = isOldMultipart
                            action.isNewMultipart = isNewMutipart
                            renameBlockEntityActions.add(action)
                            processedBlockEntities.add(oldNameStr)
                            break
                        }

                        if (mappingFound)
                            break
                    }

                    if (!mappingFound) {
                        // Block entity in new world save that is similar to the one in old world save wasn't found
                        val action = RenameBlockEntityAction()
                        action.oldName = oldName
                        action.isOldMultipart = isOldMultipart
                        missingRenameBlockEntityActions.add(action)
                    }
                }
            }
        }

        /**
         * Returns block entity names, taking into account Forge multiparts. Returns list of pairs: first - its name.
         * second - whther block entity uses Forge multipart.
         */
        private fun getRealBlockEntityNames(tag: CompoundTag): List<Pair<String, Boolean>> {
            val list = ArrayList<Pair<String, Boolean>>()
            val tmpName = tag.getTagValue<String>("id") ?: return list

            if (tmpName == "mcmultipart:multipart.ticking") {
                val tagList = tag.getTagValue<List<CompoundTag>>("partList") ?: return list
                for (entry in tagList) {
                    val id = entry.getTagValue<String>("__partType") ?: continue
                    list.add(Pair(id, true))
                }
            } else if (tmpName == "savedMultipart") {
                val tagList = tag.getTagValue<List<CompoundTag>>("parts") ?: return list
                for (entry in tagList) {
                    val id = entry.getTagValue<String>("id") ?: continue
                    list.add(Pair(id, true))
                }
            } else
                list.add(Pair(tmpName, false))

            return list
        }

        private fun updateProgress() {
            val currentProgress = progress.incrementAndGet()
            if (cooldownLock.tryLock()) {
                Logger.progress(currentProgress, totalChunks, "Processing chunks")
                cooldownLock.unlock()
            }
        }
    }

    private data class ChunkComparatorResult(
            val swapBlockActions: List<SwapBlockAction>,
            val renameBlockEntityActions: HashSet<RenameBlockEntityAction>,
            val missingRenameBlockEntityActions: HashSet<RenameBlockEntityAction>
    )

    companion object {
        private val AVAILABLE_CPUS by lazy { Runtime.getRuntime().availableProcessors() }
    }
}