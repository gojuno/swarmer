package com.gojuno.swarmer

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

// No way to share array both for runtime and annotation without reflection.
private const val PARAMETER_EMULATOR_NAME = "--emulator-name"

sealed class Commands {
    companion object {
        val ALIASES_HELP = listOf("--help", "-help", "help", "-h")
        val ALIASES_START = listOf("start")
        val ALIASES_STOP = listOf("stop")

        fun fromStringAlias(alias: String): Commands? = when {
            ALIASES_HELP.contains(alias) -> Help
            ALIASES_STOP.contains(alias) -> Stop()
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
            commandNames = arrayOf("start")
    )
    data class Start(
            @Parameter(
                    names = arrayOf(PARAMETER_EMULATOR_NAME),
                    required = true,
                    description = "Name for the emulator, i.e. `test_emulator_1` to pass to `avdmanager create avd --name`.",
                    order = 1
            )
            var emulatorName: String = "",

            @Parameter(
                    names = arrayOf("--package"),
                    required = true,
                    description = "Package of the system image for this AVD (e.g.'system-images;android-25;google_apis;x86') to pass to `avdmanager create avd --package`.",
                    order = 2
            )
            var pakage: String = "",

            @Parameter(
                    names = arrayOf("--android-abi"),
                    required = true,
                    description = "Android system image abi, i.e. `google_apis/x86_64` to pass to `avdmanager create avd --abi`.",
                    order = 3
            )
            var androidAbi: String = "",

            @Parameter(
                    names = arrayOf("--path-to-config-ini"),
                    required = true,
                    description = "Path either relative or absolute to the file that will be used as `config.ini` for created emulator.",
                    order = 4
            )
            var pathToConfigIni: String = "",

            @Parameter(
                    names = arrayOf("--emulator-start-options"),
                    required = false,
                    variableArity = true,
                    description = "Options to pass to `emulator -avd \$emulatorName` command, i.e. `--no-window -prop persist.sys.language=en -prop persist.sys.country=US`.",
                    order = 5
            )
            var emulatorStartOptions: List<String> = emptyList(),

            @Parameter(
                    names = arrayOf("--emulator-start-timeout-seconds"),
                    required = false,
                    description = "Timeout to wait for emulator to finish boot. Default value is 180 seconds.",
                    order = 6
            )
            var emulatorStartTimeoutSeconds: Long = 180,

            @Parameter(
                    names = arrayOf("--redirect-logcat-to"),
                    required = false,
                    description = "Path either relative or absolute to the file that will be used to redirect logcat of started emulator to. No redirection will happen if parameter is not presented.",
                    order = 7
            )
            var redirectLogcatTo: String? = null
    ) : Commands()

    @Parameters(
            commandDescription = "Stop all emulators",
            commandNames = arrayOf("stop")
    )
    data class Stop(
            @Parameter(
                    names = arrayOf("--timeout"),
                    required = false,
                    description = "Timeout for emulators to stop in seconds, default is 15 seconds.",
                    order = 1
            )
            var timeoutSeconds: Int = 15
    ) : Commands()
}

fun parseCommand(rawArgs: List<String>) = Commands.fromStringAlias(rawArgs[0])

fun parseStartArguments(rawArgs: List<String>): List<Commands.Start> =
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
                    Commands.Start().also { command ->
                        JCommander(command).parse(*it.toTypedArray())
                    }
                }

fun parseStopArguments(rawArgs: List<String>): Commands.Stop =
        rawArgs
                .subList(1, rawArgs.size) // skip command
                .let { stopArguments ->
                    Commands.Stop().also { command ->
                        JCommander(command).parse(*stopArguments.toTypedArray())
                    }
                }

fun printUsage() =
        JCommander.newBuilder()
                .addCommand(Commands.ALIASES_HELP.first(), Commands.Help, *Commands.ALIASES_HELP.toTypedArray())
                .addCommand(Commands.Stop())
                .addCommand(Commands.Start())
                .build()
                .usage()

