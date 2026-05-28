package com.zugaldia.speedofsound.core

/**
 * Thrown when the application encounters a non-recoverable startup error.
 *
 * Callers are expected to catch this exception and transition the app into a
 * controlled degraded state instead of crashing.
 */
open class FatalStartupException(message: String) : Exception(message)
