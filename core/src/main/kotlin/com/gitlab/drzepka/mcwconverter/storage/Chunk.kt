package com.gitlab.drzepka.mcwconverter.storage

import com.flowpowered.nbt.CompoundTag
import kotlin.experimental.and

/**
 * Represents single chunk from world save.
 *
 * @param rootTag root tag of this chunk
 * @param chunkX the X coordinate of this chunk
 * @param chunkZ the Z coordinate of this chunk
 */
class Chunk(val rootTag: CompoundTag, val chunkX: Int, val chunkZ: Int) {

    private val blockIds: Array<ShortArray?> = Array(16) { null }
    private val blockData: Array<ShortArray?> = Array(16) { null }

    init {
        // Since Minecraft 1.13 blocks are stored in another format
        var format113: Boolean? = null
        val sections = rootTag.getTagValue<List<CompoundTag>>("Level Sections")!!

        for (section in sections) {
            if (format113 == null)
                format113 = !section.value.containsKey("Blocks")

            /**
             * Reads and returns a 4-bit-long short value from byte array of block data
             */
            fun getNibble(array: ByteArray, position: Int): Int {
                return if (position % 2 == 0)
                    array[position / 2].and(0x0f).toInt()
                else
                    array[position / 2].toInt().shr(4).and(0x0f)
            }

            val blockIdSection = ShortArray(4096)
            val blockDataSection = ShortArray(4096)

            val sectionId = section.getTagValue<Byte>("Y")!!
            blockIds[sectionId.toInt()] = blockIdSection
            blockData[sectionId.toInt()] = blockDataSection

            if (!format113) {
                // Using old blcck storage format (until 1.12)
                val blocks = section.getTagValue<ByteArray>("Blocks")!!
                val add = section.getTagValue<ByteArray>("Add")
                val data = section.getTagValue<ByteArray>("Data")!!

                for (i in 0 until 4096) {
                    var id = blocks[i].toShort()
                    if (add != null)
                        id = (getNibble(add, i).shl(8).toShort() + id).toShort()

                    blockIdSection[i] = id
                    blockDataSection[i] = getNibble(data, i).toShort()
                }

            } else {
                // Using new block storage format (since 1.13)
                TODO("Minecraft 1.13 block storage format isn't implemented yet")
            }
        }
    }

    /**
     * Returns id of a block. Coordinates are relative to chunk (0 to 15 or 0 to 255, inclusive).
     * @return id of a block or `-1` if there is no section at given coordinates
     */
    fun getBlockId(x: Int, y: Int, z: Int): Short = getArrayValue(x, y, z, blockIds)

    /**
     * Returns metadata of a block. Coordinates are relative to chunk (0 to 15 or 0 to 255, inclusive).
     * @return metadata of a block or `-1` if there is no section at given coordinates
     */
    fun getBlockMeta(x: Int, y: Int, z: Int): Short = getArrayValue(x, y, z, blockData)

    /**
     * Returns whether this chunk contains given section. Chunk has 16 sections (range 0 to 15, bottom to top) and every
     * of them covers a height of 16 blocks.
     */
    fun hasSection(section: Int): Boolean = blockIds[section] != null

    @Suppress("NOTHING_TO_INLINE")
    private inline fun getArrayValue(x: Int, y: Int, z: Int, array: Array<ShortArray?>): Short {
        val section = Math.floor(y / 16.0).toInt()
        if (array[section] == null)
            return 0

        val pos = y * 256 + z * 16 + x
        return array[section]?.get(pos) ?: 0
    }
}