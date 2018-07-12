package com.gitlab.drzepka.mcwconverter

/**
 * An exception which message can be displayed to user. Stack trace shouldn't be displayed though.
 */
class PrintableException(message: String) : Exception(message)