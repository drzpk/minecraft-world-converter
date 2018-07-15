package com.gitlab.drzepka.mcwconverter

import com.gitlab.drzepka.mcwconverter.storage.RegionStorage
import org.junit.Assert
import org.junit.Test
import java.io.File

/**
 * Various storage tests
 */
class StorageTest {

    /**
     * Tests whether block data is loaded and read correctly.
     */
    @Test
    fun blockDataTest() {
        val region = loadTestRegion()
        fun testBlock(x: Int, y: Int, z: Int, expectedId: Short, expectedMeta: Short) {
            val blockId = region.getBlockId(x, y, z)
            Assert.assertEquals(
                    "wrong block id at ($x, $y, $z). Expected $expectedId, got $blockId",
                    expectedId,
                    blockId
            )

            val blockMeta = region.getBlockMeta(x, y, z)
            Assert.assertEquals(
                    "wrong block meta at ($x, $y, $z). Expected $expectedMeta, got $blockMeta",
                    expectedMeta,
                    blockMeta
            )
        }

        testBlock(-542, 60, -4112, 1, 0)
        testBlock(-535, 86, -4099, 31, 1)
        testBlock(-520, 67, -4109, 162, 0)
        testBlock(-520, 69, -4110, 85, 0)
        testBlock(-517, 97, -4122, 919, 1)
    }

    private fun loadTestRegion(): RegionStorage {
        // Old, pre 1.13 storage format
        val uri = this::class.java.getResource("/com/gitlab/drzepka/mcwconverter/r.-2.-9.mca")
        Assert.assertNotNull("couldn't load test region resource", uri)
        val regionFile = File(uri.toURI())
        return RegionStorage(regionFile)
    }
}