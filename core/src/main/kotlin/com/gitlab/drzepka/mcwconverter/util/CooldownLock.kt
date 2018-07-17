package com.gitlab.drzepka.mcwconverter.util

import com.gitlab.drzepka.mcwconverter.McWConverter
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * A lock with cooldown feature: after unlocking the amount of time specified in the [cooldown] parameter must pass
 * before new lock can be acquired.
 *
 * @param cooldown lock cooldown in milliseconds
 */
class CooldownLock(private val cooldown: Long) : ReentrantLock() {

    private val reference = McWConverter.PRINT_LOCK
    private var lastReleased = 0L

    override fun lock() {
        reference.lock()
        execCooldown()
    }

    override fun lockInterruptibly() {
        reference.lockInterruptibly()
        execCooldown()
    }

    override fun tryLock(): Boolean = System.currentTimeMillis() > lastReleased + cooldown && reference.tryLock()

    override fun tryLock(timeout: Long, unit: TimeUnit?): Boolean = System.currentTimeMillis() > lastReleased + cooldown && reference.tryLock(timeout, unit)

    override fun unlock() {
        lastReleased = System.currentTimeMillis()
        reference.unlock()
    }

    private fun execCooldown() {
        val current = System.currentTimeMillis()
        if (current < lastReleased + cooldown)
            Thread.sleep(cooldown + lastReleased - current)
    }
}