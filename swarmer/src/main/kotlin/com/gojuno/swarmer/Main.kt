package com.gojuno.swarmer

fun main(vararg rawArgs: String) {
    rawArgs.toList().apply {
        when (parseCommand(this)) {
            is Commands.Start -> {
                startEmulators(parseStartArguments(this))
            }
            is Commands.Stop -> {
                stopAllEmulators(parseStopArguments(this))
            }
            Commands.Help -> {
                printUsage()
            }
            else -> {
                println("Invalid command!")
                printUsage()
            }
        }
    }

    System.exit(0) // Force exit, emulator and logcat redirect will keep running as detached processes.
}
