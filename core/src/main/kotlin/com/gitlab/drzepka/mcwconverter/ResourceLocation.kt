package com.gitlab.drzepka.mcwconverter

/**
 * This class is an imitation of the original one from Forge.
 */
class ResourceLocation() {

    var domain = ""
        set(value) {
            field = value
            updateFullName()
        }
    var path = ""
        set(value) {
            field = value
            updateFullName()
        }
    var fullName = ""

    constructor(name: String) : this() {
        val parts = name.split(":", limit = 2)
        if (parts.size == 1) {
            domain = ""
            path = parts[0]
        } else {
            domain = parts[0]
            path = parts[1]
        }
    }

    constructor(domain: String, value: String) : this() {
        this.domain = domain
        this.path = value
    }

    /**
     * Returns whether this resource location is similar to another (string-encoded). Domains are compared literally
     * whereas paths by similarity.
     */
    fun isSimilarTo(name: String): Boolean {
        val parts = name.split(":")
        if (parts.size != 2)
            return false

        if (parts[0] != domain)
            return false

        // Sometimes path changes between versions. Usually camelCase string is changed to underscore_based
        // or the other way around.
        return getRawString(path) == getRawString(parts[1])
    }

    override fun toString(): String {
        return if (domain.isNotBlank()) "$domain:$path" else path
    }

    private fun getRawString(source: String): String = source.toLowerCase().replace("_", "")

    private fun updateFullName() {
        fullName = "$domain:$path"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ResourceLocation)
            return false

        return other.fullName == fullName
    }

    override fun hashCode(): Int = fullName.hashCode()
}