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

    init {
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

        val array = ByteArray(4096)
        regionFile.seek(0)
        regionFile.read(array)
        val buffer = ByteBuffer.wrap(array)
        buffer.order(ByteOrder.BIG_ENDIAN)
        for (i in 0 until 1024) {
            val location = buffer.getInt(i * 4)
            if (location == 0) {
                // Current chunk is empty
                // Returning null chunks will help in calculations
                chunks.add(null)
                continue
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

            val chunkX = regionX * 32 + (i % 32)
            val chunkZ = regionZ * 32 + Math.floor(i / 32.toDouble()).toInt()
            chunks.add(Chunk(tag, chunkX, chunkZ))
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
}