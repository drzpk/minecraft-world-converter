package com.gitlab.drzepka.mcwconverter.level

import com.flowpowered.nbt.CompoundTag
import com.gitlab.drzepka.mcwconverter.Logger
import com.gitlab.drzepka.mcwconverter.PrintableException
import com.gitlab.drzepka.mcwconverter.ResourceLocation
import com.gitlab.drzepka.mcwconverter.action.RenameBlockAction
import com.gitlab.drzepka.mcwconverter.action.RenameItemAction
import com.gitlab.drzepka.mcwconverter.action.SwapBlockAction
import com.gitlab.drzepka.mcwconverter.storage.Chunk
import com.gitlab.drzepka.mcwconverter.storage.LevelStorage
import com.gitlab.drzepka.mcwconverter.storage.getTagValue
import com.gitlab.drzepka.mcwconverter.util.CooldownLock
import java.io.File
import java.io.PrintWriter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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
    }

    /**
     * Compares two levels and finds dirrefences between them.
     *
     * @param [output] writer analyze result will be written to
     */
    fun compare(output: PrintWriter) {
        logGenerator = LogGenerator(output)
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
        val uniqueSwapBlockActions = HashSet<SwapBlockAction>()
        for (thread in threads) {
            val result = thread.get()
            uniqueSwapBlockActions.addAll(result.swapBlockActions)
        }
        val swapBlockActions = uniqueSwapBlockActions.sortedBy { it.sortableStr }.toMutableList()

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

        // Append section to a log file
        swapBlockActions.forEach { it.blockMappings = blockMappings }
        logGenerator.generateSection(swapBlockActions, "doc_swap_blocks.txt", true)
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
    private inner class ChunkComparator : Callable<ChunkComparatorResult> {

        override fun call(): ChunkComparatorResult {
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
                                if (oldChunk.getBlockId(x, realY, z) != 0.toShort() && newChunk.getBlockId(x, realY, z) == 0.toShort()) {
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

                updateProgress()
            }

            return ChunkComparatorResult(swapBlockActions)
        }

        private fun updateProgress() {
            val currentProgress = progress.incrementAndGet()
            if (cooldownLock.tryLock()) {
                Logger.progress(currentProgress, totalChunks, "Processing chunks")
                cooldownLock.unlock()
            }
        }
    }

    private data class ChunkComparatorResult(val swapBlockActions: List<SwapBlockAction>)

    companion object {
        private val AVAILABLE_CPUS by lazy { Runtime.getRuntime().availableProcessors() }
    }
}