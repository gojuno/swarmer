package com.gojuno.swarmer

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter

data class Args(
        val emulatorName: String,
        val pakage: String,
        val androidAbi: String,
        val pathToConfigIni: String,
        val emulatorStartOptions: List<String>,
        val emulatorStartTimeoutSeconds: Long,
        val redirectLogcatTo: String?
)

// No way to share array both for runtime and annotation without reflection.
private val PARAMETER_HELP_NAMES = setOf("--help", "-help", "help", "-h")
private const val PARAMETER_EMULATOR_NAME = "--emulator-name"

private class JCommanderArgs {

    @Parameter(
            names = arrayOf("--help", "-help", "help", "-h"),
            help = true,
            description = "Print help and exit."
    )
    var help: Boolean? = null

    @Parameter(
            names = arrayOf(PARAMETER_EMULATOR_NAME),
            required = true,
            description = "Name for the emulator, i.e. `test_emulator_1` to pass to `avdmanager create avd --name`."
    )
    lateinit var emulatorName: String

    @Parameter(
            names = arrayOf("--package"),
            required = true,
            description = "Package of the system image for this AVD (e.g.'system-images;android-25;google_apis;x86') to pass to `avdmanager create avd --package`."
    )
    lateinit var pakage: String

    @Parameter(
            names = arrayOf("--android-abi"),
            required = true,
            description = "Android system image abi, i.e. `google_apis/x86_64` to pass to `avdmanager create avd --abi`."
    )
    lateinit var androidAbi: String

    @Parameter(
            names = arrayOf("--path-to-config-ini"),
            required = true,
            description = "Path either relative or absolute to the file that will be used as `config.ini` for created emulator."
    )
    lateinit var pathToConfigIni: String

    @Parameter(
            names = arrayOf("--emulator-start-options"),
            required = false,
            variableArity = true,
            description = "Options to pass to `emulator -avd \$emulatorName` command, i.e. `--no-window -prop persist.sys.language=en -prop persist.sys.country=US`."
    )
    var emulatorStartOptions: List<String>? = null

    @Parameter(
            names = arrayOf("--emulator-start-timeout-seconds"),
            required = false,
            description = "Timeout to wait for emulator to finish boot. Default value is 180 seconds."
    )
    var emulatorStartTimeoutSeconds: Long? = null

    @Parameter(
            names = arrayOf("--redirect-logcat-to"),
            required = false,
            description = "Path either relative or absolute to the file that will be used to redirect logcat of started emulator to. No redirection will happen if parameter is not presented.")
    var redirectLogcatTo: String? = null
}

fun parseArgs(rawArgs: List<String>): List<Args> {
    if (PARAMETER_HELP_NAMES.firstOrNull { rawArgs.contains(it) } != null) {
        JCommander(JCommanderArgs()).usage()
        System.exit(0)
    }

    // PR to rewrite this mess to Rx or Kotlin Stream API very welcome.

    fun List<String>.indexOfFirstParameterEmulatorName(): Int = indexOfFirst { it == PARAMETER_EMULATOR_NAME }

    fun List<String>.indexOfSecondParameterEmulatorName(): Int {
        val indexOfFirst = indexOfFirstParameterEmulatorName()

        return when (indexOfFirst) {
            -1 -> -1
            else -> {
                if (indexOfFirst == 0 && size > 1) {
                    subList(indexOfFirst + 1, size).indexOfFirstParameterEmulatorName().let { if (it == -1) it else it + 1 }
                } else {
                    -1
                }
            }
        }
    }

    var indexOfFirstParameterEmulatorName = rawArgs.indexOfFirstParameterEmulatorName()
    var indexOfSecondParameterEmulatorName = rawArgs.indexOfSecondParameterEmulatorName()

    var argsLeft = rawArgs
    var splitArgs = emptyList<List<String>>()

    while (indexOfFirstParameterEmulatorName != -1) {
        splitArgs += listOf(argsLeft.subList(indexOfFirstParameterEmulatorName, indexOfSecondParameterEmulatorName.let { if (it == -1) argsLeft.size else it }))
        argsLeft = if (indexOfSecondParameterEmulatorName == -1)
            emptyList()
        else
            argsLeft.subList(indexOfSecondParameterEmulatorName, argsLeft.size)

        indexOfFirstParameterEmulatorName = argsLeft.indexOfFirstParameterEmulatorName()
        indexOfSecondParameterEmulatorName = argsLeft.indexOfSecondParameterEmulatorName()
    }

    return splitArgs.map { parseEachArgSet(it) }
}

private fun parseEachArgSet(rawArgs: List<String>): Args {
    val jCommanderArgs = JCommanderArgs()

    JCommander(jCommanderArgs, *rawArgs.toTypedArray())

    return jCommanderArgs.let {
        Args(
                emulatorName = it.emulatorName,
                pakage = it.pakage,
                androidAbi = it.androidAbi,
                pathToConfigIni = it.pathToConfigIni,
                emulatorStartOptions = it.emulatorStartOptions ?: emptyList(),
                emulatorStartTimeoutSeconds = it.emulatorStartTimeoutSeconds ?: 180,
                redirectLogcatTo = it.redirectLogcatTo
        )
    }
}

