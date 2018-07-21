package com.gitlab.drzepka.mcwconverter

import com.gitlab.drzepka.mcwconverter.action.*
import org.junit.Test
import java.util.*
import java.util.function.Supplier

/**
 * Tests whether actions are being serialized and parsed correctly.
 */
class ActionParsingTest {

    private val iterations = 20
    private val random = Random()

    // TODO: test actions that have syntax errors and aren't supposed to parse successfully

    @Test
    fun testRenameBlockAction() {
        testAction(Supplier {
            val action = RenameBlockAction()
            action.oldId = random.nextInt(4000)
            action.newId = random.nextInt(4000)
            action.oldName = randomResourceLocation()
            action.newName = randomResourceLocation()
            action
        })
    }

    @Test
    fun testRenameItemAction() {
        testAction(Supplier {
            val action = RenameItemAction()
            action.oldId = random.nextInt(4000)
            action.newId = random.nextInt(4000)
            action.oldName = randomResourceLocation()
            action.newName = randomResourceLocation()
            action
        })
    }

    @Test
    fun testRenameBlockEntityAction() {
        testAction(Supplier {
            val action = RenameBlockEntityAction()
            action.oldName = randomResourceLocation()
            action.newName = randomResourceLocation()
            action.isOldMultipart = random.nextBoolean()
            action.isNewMultipart = if (action.isOldMultipart) random.nextBoolean() else false
            action
        })
    }

    @Test
    fun testSwapBlockAction() {
        val blockMappings = ArrayList<Pair<Int, String>>()
        repeat(100) {
            val mapping = Pair(random.nextInt(4000), randomResourceLocation().toString())
            blockMappings.add(mapping)
        }

        testAction(Supplier {
            val action = SwapBlockAction()
            if (random.nextBoolean())
                action.oldId = blockMappings[random.nextInt(blockMappings.size)].first.toShort()
            else
                action.oldId = random.nextInt(4000).toShort()
            if (random.nextBoolean())
                action.newId = blockMappings[random.nextInt(blockMappings.size)].first.toShort()
            else
                action.newId = random.nextInt(4000).toShort()
            action.oldMeta = random.nextInt(16).toShort()
            action.newMeta = random.nextInt(16).toShort()
            action.blockMappings = blockMappings
            action
        })
    }

    private fun <T : BaseAction> testAction(supplier: Supplier<T>) {
        // These actions are parsed normally
        repeat(iterations) {
            val action = supplier.get()
            val serialized = action.toString()

            try {
                val parsed = action.parse(serialized)
                if (action != parsed) {
                    println("Actions of type ${action.actionName} differ:")
                    println("Old: $action")
                    println("New: $action")
                    throw Exception("Action couldn't be recreated after parsing")
                }
            } catch (exception: Throwable) {
                System.err.println("Action reconstruction failed:")
                System.err.println("${action.actionName} $action")
                throw exception
            }
        }
    }

    private fun randomResourceLocation(): ResourceLocation {
        val builder = StringBuilder()
        val stringLength = random.nextInt(20) + 10
        val domainLength = random.nextInt(stringLength / 2) + stringLength / 4

        (0 until stringLength).forEach {
            val char = (random.nextInt(26) + 97).toChar()
            builder.append(if (random.nextBoolean()) char.toUpperCase() else char)
        }

        builder.insert(domainLength, ':')
        return ResourceLocation(builder.toString())
    }
}