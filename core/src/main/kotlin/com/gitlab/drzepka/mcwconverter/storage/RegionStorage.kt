package com.gitlab.drzepka.mcwconverter.storage

import com.flowpowered.nbt.CompoundTag
import com.flowpowered.nbt.stream.NBTInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.zip.InflaterInputStream

/**
 * Loads region file and returns NBT-encoded chunks.
 *
 * @throws IllegalStateException when something went wrong
 */
class RegionStorage(regionFile: File) {

    /** The X coordinate of this region file. */
    val regionX: Int
    /** The Z coordinate of this region file. */
    val regionZ: Int

    private val regionFile: RandomAccessFile
    private val chunkCache: LinkedHashMap<Int, Chunk>
    private var chunkLocationBuffer: ByteBuffer? = null

    init {
        // Create cache to store most recent chunks in memory
        chunkCache = object : LinkedHashMap<Int, Chunk>(3, 1f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Chunk>?): Boolean {
                return size > 3
            }
        }

        if (!regionFile.isFile)
            throw IllegalStateException("file doesn't exist")
        if (regionFile.length() < 8192)
            throw IllegalStateException("file is too short")

        this.regionFile = RandomAccessFile(regionFile, "r")

        val coordParts = regionFile.nameWithoutExtension.split(".")
        try {
            regionX = coordParts[1].toInt()
            regionZ = coordParts[2].toInt()
        } catch (ignored: Exception) {
            throw IllegalStateException("file isn't an region")
        }
    }

    /**
     * Reads and returns all chunks from region file.
     */
    fun readChunks(): List<Chunk?> {
        val chunks = ArrayList<Chunk?>(1024)

        for (i in 0 until 1024) {
            val chunk = loadChunk(i)
            chunks.add(chunk)
        }

        return chunks
    }

    /**
     * Returns amount of populated chunks stored in this region without loading them into memory.
     */
    fun countChunks(): Int {
        val array = ByteArray(4096)
        regionFile.seek(0)
        regionFile.read(array)
        val buffer = ByteBuffer.wrap(array)
        return (0 until 1024).map { buffer.getInt(it * 4) }.count { it > 0 }
    }

    /**
     * Returns block id at given position. Coordinates are worldwide.
     */
    fun getBlockId(x: Int, y: Int, z: Int): Short {
        val chunk = getCachedChunk(x, z)
        var localX = x.rem(16)
        if (localX < 0) localX += 16
        var localZ = z.rem(16)
        if (localZ < 0) localZ += 16
        return chunk?.getBlockId(localX, y, localZ) ?: 0
    }

    /**
     * Return block meta at given position. Coordinates are worldwide.
     */
    fun getBlockMeta(x: Int, y: Int, z: Int): Short {
        val chunk = getCachedChunk(x, z)
        var localX = x.rem(16)
        if (localX < 0) localX += 16
        var localZ = z.rem(16)
        if (localZ < 0) localZ += 16
        return chunk?.getBlockMeta(localX, y, localZ) ?: 0
    }

    /**
     * Returns chunk based on worldwide block coordinates.
     */
    private fun getCachedChunk(x: Int, z: Int): Chunk? {
        val worldRegionX = x.shr(9)
        val worldRegionZ = z.shr(9)
        if (worldRegionX != regionX || worldRegionZ != regionZ) {
            // Block doesn't exist in this region
            return null
        }

        // Calculate chunk coords within this region
        val localChunkX = Math.floor(x / 16.0 - regionX * 32).toInt()
        val localChunkZ = Math.floor(z / 16.0 - regionZ * 32).toInt()
        val chunkId = localChunkZ * 32 + localChunkX

        if (chunkCache.containsKey(chunkId))
            return chunkCache[chunkId]

        val chunk = loadChunk(chunkId)
        if (chunk != null)
            chunkCache[chunkId] = chunk

        return chunk
    }

    private fun loadChunk(chunkId: Int): Chunk? {
        if (chunkLocationBuffer == null) {
            val array = ByteArray(4096)
            regionFile.seek(0)
            regionFile.read(array)
            chunkLocationBuffer = ByteBuffer.wrap(array)
            chunkLocationBuffer?.order(ByteOrder.BIG_ENDIAN)
        }

        val location = chunkLocationBuffer!!.getInt(chunkId * 4)
        if (location == 0) {
            // Current chunk is empty
            // Returning null chunks will help in calculations
            return null
        }

        val offset = location ushr 8
        regionFile.seek(offset * 4096.toLong())
        val exactSize = regionFile.readInt()
        regionFile.seek(offset * 4096.toLong() + 5)
        val chunkArray = ByteArray(exactSize - 1)
        regionFile.read(chunkArray)

        val tagStream = NBTInputStream(InflaterInputStream(ByteArrayInputStream(chunkArray)), false)
        val tag = tagStream.readTag() as CompoundTag
        tagStream.close()

        val chunkX = regionX * 32 + (chunkId % 32)
        val chunkZ = regionZ * 32 + Math.floor(chunkId / 32.toDouble()).toInt()

        return Chunk(tag, chunkX, chunkZ)
    }
}