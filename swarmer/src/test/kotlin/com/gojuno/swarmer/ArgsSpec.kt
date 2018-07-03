package com.gojuno.swarmer

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

class ArgsSpec : Spek({

    val REQUIRED_ARGS = listOf(
            "--emulator-name", "test_emulator_name",
            "--package", "test_android_package",
            "--android-abi", "test_android_abi",
            "--path-to-config-ini", "test_path_to_config_ini"
    )

    val OPTIONAL_ARGS = listOf(
            "--emulator-start-options", "-prop option=value",
            "--emulator-start-timeout-seconds", "180",
            "--redirect-logcat-to", "logcat.txt",
            "--verbose-emulator", "--keep-output-on-exit",
            "--keep-existing-avds"
    )

    on("parse args with only required fields") {

        val result by memoized {
            parseStartArguments(listOf("start") + REQUIRED_ARGS)
        }

        it("parses passed args and uses default values for non-required fields") {
            assertThat(result).isEqualTo(listOf(Commands.Start(
                    emulatorName = "test_emulator_name",
                    pakage = "test_android_package",
                    androidAbi = "test_android_abi",
                    pathToConfigIni = "test_path_to_config_ini"
            )))
        }
    }

    on("parse args with all fields") {

        val result by memoized {
            parseStartArguments(listOf("start") + REQUIRED_ARGS + OPTIONAL_ARGS)
        }

        it("parses passed args and uses default values for non-required fields") {
            assertThat(result).isEqualTo(listOf(Commands.Start(
                    emulatorName = "test_emulator_name",
                    pakage = "test_android_package",
                    androidAbi = "test_android_abi",
                    pathToConfigIni = "test_path_to_config_ini",
                    emulatorStartOptions = listOf("-prop option=value"),
                    emulatorStartTimeoutSeconds = 180L,
                    redirectLogcatTo = "logcat.txt",
                    verbose = true,
                    keepOutputOnExit = true,
                    keepExistingAvds = true
            )))
        }
    }

    on("parse multiple args") {

        val result by memoized {
            parseStartArguments(listOf(
                    "start",
                    "--emulator-name", "test_emulator_name_1",
                    "--package", "test_android_package_1",
                    "--android-abi", "test_android_abi_1",
                    "--path-to-config-ini", "test_path_to_config_ini_1",
                    "--emulator-name", "test_emulator_name_2",
                    "--package", "test_android_package_2",
                    "--android-abi", "test_android_abi_2",
                    "--path-to-config-ini", "test_path_to_config_ini_2"
            ))
        }

        it("parses two arguments") {
            assertThat(result).isEqualTo(listOf(
                    Commands.Start(
                            emulatorName = "test_emulator_name_1",
                            pakage = "test_android_package_1",
                            androidAbi = "test_android_abi_1",
                            pathToConfigIni = "test_path_to_config_ini_1"
                    ),
                    Commands.Start(
                            emulatorName = "test_emulator_name_2",
                            pakage = "test_android_package_2",
                            androidAbi = "test_android_abi_2",
                            pathToConfigIni = "test_path_to_config_ini_2"
                    )
            ))
        }
    }

    arrayOf(
            "--help", "-help", "help", "-h"
    ).forEach { alias ->
        on("parses help command for alias : $alias") {

            val result by memoized {
                parseCommand(listOf(alias))
            }

            it("parses correct command") {
                assertThat(result).isEqualTo(Commands.Help)
            }
        }
    }

    on("parses start command") {

        val result by memoized {
            parseCommand(listOf("start") + REQUIRED_ARGS)
        }

        it("parses correct command") {
            assertThat(result).isEqualTo(Commands.Start())
        }
    }

    on("stop command passed") {

        val result by memoized {
            parseCommand(listOf("stop"))
        }

        it("parses correct command") {
            assertThat(result).isEqualTo(Commands.Stop())
        }
    }
})
