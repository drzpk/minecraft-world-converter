package com.gitlab.drzepka.mcwconverter.action

/**
 * Represents an action that can be performed
 */
abstract class BaseAction {

    /** The name of this action. All lines (except comments) in analyze result log start with action name. */
    abstract val actionName: String
    /** Returns string that will be used to sort actions. (More efficient than the [toString] method) */
    abstract var sortableStr: String

    /**
     * Returns string representation of this action. It will be later parsed by the [parse] method. Return action-specific
     * content only, without actionName.
     */
    abstract override fun toString(): String

    /**
     * Parses this action.
     * @param source content serialized in the [toString] method.
     * @return new action object
     * @throws [com.gitlab.drzepka.mcwconverter.PrintableException] when there is a parse error
     */
    abstract fun parse(source: String): BaseAction

    abstract override fun equals(other: Any?): Boolean
    abstract override fun hashCode(): Int
}