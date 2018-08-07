package com.gojuno.swarmer

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import java.util.*
import java.util.Collections.emptyList

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
            commandNames = ["--help", "-help", "help", "-h"]
    )
    object Help : Commands()

    @Parameters(
            commandDescription = "Start emulators listed",
            commandNames = ["start"]
    )
    data class Start(
            @Parameter(
                    names = [PARAMETER_EMULATOR_NAME],
                    required = true,
                    description = "Name for the emulator, i.e. `test_emulator_1` to pass to `avdmanager create avd --name`.",
                    order = 1
            )
            var emulatorName: String = "",

            @Parameter(
                    names = ["--package"],
                    required = true,
                    description = "Package of the system image for this AVD (e.g.'system-images;android-25;google_apis;x86') to pass to `avdmanager create avd --package`.",
                    order = 2
            )
            var pakage: String = "",

            @Parameter(
                    names = ["--android-abi"],
                    required = true,
                    description = "Android system image abi, i.e. `google_apis/x86_64` to pass to `avdmanager create avd --abi`.",
                    order = 3
            )
            var androidAbi: String = "",

            @Parameter(
                    names = ["--path-to-config-ini"],
                    required = true,
                    description = "Path either relative or absolute to the file that will be used as `config.ini` for created emulator.",
                    order = 4
            )
            var pathToConfigIni: String = "",

            @Parameter(
                    names = ["--emulator-start-options"],
                    required = false,
                    variableArity = true,
                    description = "Options to pass to `emulator -avd \$emulatorName` command, i.e. `--no-window -prop persist.sys.language=en -prop persist.sys.country=US`.",
                    order = 5
            )
            var emulatorStartOptions: List<String> = emptyList(),

            @Parameter(
                    names = ["--emulator-start-timeout-seconds"],
                    required = false,
                    description = "Timeout to wait for emulator to finish boot. Default value is 180 seconds.",
                    order = 6
            )
            var emulatorStartTimeoutSeconds: Long = 180,

            @Parameter(
                    names = ["--redirect-logcat-to"],
                    required = false,
                    description = "Path either relative or absolute to the file that will be used to redirect logcat of started emulator to. No redirection will happen if parameter is not presented.",
                    order = 7
            )
            var redirectLogcatTo: String? = null,

            @Parameter(
                    names = ["--redirect-output-to"],
                    required = false,
                    description = "Path either relative or absolute to the directory that will be used to redirect emulator command output. No redirection will happen if parameter is not presented.",
                    order = 8
            )
            var redirectOutputTo: String? = null,

            @Parameter(
                    names = ["--verbose-emulator"],
                    required = false,
                    description = "Print verbose emulator initialization messages.",
                    order = 9
            )
            var verbose: Boolean = false,

            @Parameter(
                    names = ["--keep-output-on-exit"],
                    required = false,
                    description = "Keep output of emulator command on exit. False by default.",
                    order = 10
            )
            var keepOutputOnExit: Boolean = false,

            @Parameter(
                    names = ["--use-compat-emulator"],
                    required = false,
                    description = "Use old compat emulator tool. Look https://issuetracker.google.com/issues/66886035 for details. False by default.",
                    order = 11
            )
            var useCompatEmulator: Boolean = false,

            @Parameter(
                    names = ["--keep-existing-avds"],
                    required = false,
                    description = "Don't recreate avds if one with the same name already exists.",
                    order = 12
            )
            var keepExistingAvds: Boolean = false

    ) : Commands()

    @Parameters(
            commandDescription = "Stop all emulators",
            commandNames = ["stop"]
    )
    data class Stop(
            @Parameter(
                    names = ["--timeout"],
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
                .map { args ->
                    Commands.Start().also { command ->
                        JCommander(command).parse(*args.toTypedArray())
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

