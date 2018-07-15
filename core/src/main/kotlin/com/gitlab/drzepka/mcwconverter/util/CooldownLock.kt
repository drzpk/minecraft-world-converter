package com.gitlab.drzepka.mcwconverter.util

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * A lock with cooldown feature: after unlocking the amount of time specified in the [cooldown] parameter must pass
 * before new lock can be acquired.
 *
 * @param cooldown lock cooldown in milliseconds
 */
class CooldownLock(private val cooldown: Long) : ReentrantLock() {

    private var lastReleased = 0L

    override fun lock() {
        super.lock()
        execCooldown()
    }

    override fun lockInterruptibly() {
        super.lockInterruptibly()
        execCooldown()
    }

    override fun tryLock(): Boolean {
        val result = super.tryLock()
        if (result)
            execCooldown()
        return result
    }

    override fun tryLock(timeout: Long, unit: TimeUnit?): Boolean {
        val result = super.tryLock(timeout, unit)
        if (result)
            execCooldown()
        return result
    }

    override fun unlock() {
        lastReleased = System.currentTimeMillis()
        super.unlock()
    }

    private fun execCooldown() {
        val current = System.currentTimeMillis()
        if (current < lastReleased + cooldown)
            Thread.sleep(cooldown + lastReleased - current)
    }
}