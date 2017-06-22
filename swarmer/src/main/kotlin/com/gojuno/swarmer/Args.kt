package com.gojuno.swarmer

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

// No way to share array both for runtime and annotation without reflection.
private const val PARAMETER_EMULATOR_NAME = "--emulator-name"

sealed class Commands {
    companion object {
        val ALIASES_HELP = listOf("--help", "-help", "help", "-h")
        val ALIASES_START = listOf("--start", "-start", "start")
        val ALIASES_STOP = listOf("--stop", "-stop", "stop")

        fun fromStringAlias(alias: String): Commands? = when {
            ALIASES_HELP.contains(alias) -> Help
            ALIASES_STOP.contains(alias) -> Stop
            ALIASES_START.contains(alias) -> Start()
            else -> null
        }
    }

    @Parameters(
            commandDescription = "Print help and exit.",
            commandNames = arrayOf("--help", "-help", "help", "-h")
    )
    object Help : Commands()

    @Parameters(
            commandDescription = "Start emulators listed",
            commandNames = arrayOf("--start", "-start", "start")
    )
    class Start : Commands() {
        data class ParsedArguments(
                val emulatorName: String,
                val pakage: String,
                val androidAbi: String,
                val pathToConfigIni: String,
                val emulatorStartOptions: List<String>,
                val emulatorStartTimeoutSeconds: Long,
                val redirectLogcatTo: String?
        )

        @Parameter(
                names = arrayOf(PARAMETER_EMULATOR_NAME),
                required = true,
                description = "Name for the emulator, i.e. `test_emulator_1` to pass to `avdmanager create avd --name`.",
                order = 1
        )
        lateinit var emulatorName: String

        @Parameter(
                names = arrayOf("--package"),
                required = true,
                description = "Package of the system image for this AVD (e.g.'system-images;android-25;google_apis;x86') to pass to `avdmanager create avd --package`.",
                order = 2
        )
        lateinit var pakage: String

        @Parameter(
                names = arrayOf("--android-abi"),
                required = true,
                description = "Android system image abi, i.e. `google_apis/x86_64` to pass to `avdmanager create avd --abi`.",
                order = 3
        )
        lateinit var androidAbi: String

        @Parameter(
                names = arrayOf("--path-to-config-ini"),
                required = true,
                description = "Path either relative or absolute to the file that will be used as `config.ini` for created emulator.",
                order = 4
        )
        lateinit var pathToConfigIni: String

        @Parameter(
                names = arrayOf("--emulator-start-options"),
                required = false,
                variableArity = true,
                description = "Options to pass to `emulator -avd \$emulatorName` command, i.e. `--no-window -prop persist.sys.language=en -prop persist.sys.country=US`.",
                order = 5
        )
        var emulatorStartOptions: List<String>? = null

        @Parameter(
                names = arrayOf("--emulator-start-timeout-seconds"),
                required = false,
                description = "Timeout to wait for emulator to finish boot. Default value is 180 seconds.",
                order = 6
        )
        var emulatorStartTimeoutSeconds: Long? = null

        @Parameter(
                names = arrayOf("--redirect-logcat-to"),
                required = false,
                description = "Path either relative or absolute to the file that will be used to redirect logcat of started emulator to. No redirection will happen if parameter is not presented.",
                order = 7
        )
        var redirectLogcatTo: String? = null
    }

    @Parameters(
            commandDescription = "Stop all emulators",
            commandNames = arrayOf("--stop", "-stop", "stop")
    )
    object Stop : Commands()
}

fun parseCommand(rawArgs: List<String>) = Commands.fromStringAlias(rawArgs[0])

fun parseArguments(rawArgs: List<String>): List<Commands.Start.ParsedArguments> =
        rawArgs
                .subList(1, rawArgs.size) // skip command
                .fold(ArrayList<ArrayList<String>>()) { accumulator, value ->
                    accumulator.apply {
                        if (value == PARAMETER_EMULATOR_NAME) {
                            add(arrayListOf(value))
                        } else {
                            last().add(value)
                        }
                    }
                }
                .map {
                    parseStartEmulatorArgument(it)
                }

private fun parseStartEmulatorArgument(singleEmulatorArgs: List<String>) =
        Commands.Start()
                .apply {
                    JCommander(this).parse(*singleEmulatorArgs.toTypedArray())
                }
                .let {
                    Commands.Start.ParsedArguments(
                            emulatorName = it.emulatorName,
                            pakage = it.pakage,
                            androidAbi = it.androidAbi,
                            pathToConfigIni = it.pathToConfigIni,
                            emulatorStartOptions = it.emulatorStartOptions ?: emptyList(),
                            emulatorStartTimeoutSeconds = it.emulatorStartTimeoutSeconds ?: 180,
                            redirectLogcatTo = it.redirectLogcatTo
                    )
                }

fun printUsage() =
        JCommander.newBuilder()
                .addCommand(Commands.ALIASES_HELP.first(), Commands.Help, *Commands.ALIASES_HELP.toTypedArray())
                .addCommand(Commands.ALIASES_STOP.first(), Commands.Stop, *Commands.ALIASES_STOP.toTypedArray())
                .addCommand(Commands.ALIASES_START.first(), Commands.Start(), *Commands.ALIASES_START.toTypedArray())
                .build()
                .usage()

