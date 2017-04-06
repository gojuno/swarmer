package com.gojuno.swarmer

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

class ArgsSpec : Spek({

    on("parse args with only required fields") {

        val result by memoized {
            parseArgs(listOf(
                    "--emulator-name", "test_emulator_name",
                    "--package", "test_android_package",
                    "--android-abi", "test_android_abi",
                    "--path-to-config-ini", "test_path_to_config_ini"
            ))
        }

        it("parses passed args and uses default values for non-required fields") {
            assertThat(result).isEqualTo(listOf(Args(
                    emulatorName = "test_emulator_name",
                    pakage = "test_android_package",
                    androidAbi = "test_android_abi",
                    pathToConfigIni = "test_path_to_config_ini",
                    emulatorStartOptions = emptyList(),
                    emulatorStartTimeoutSeconds = 180,
                    redirectLogcatTo = null
            )))
        }
    }

    on("parse multiple args") {

        val result by memoized {
            parseArgs(listOf(
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
                    Args(
                            emulatorName = "test_emulator_name_1",
                            pakage = "test_android_package_1",
                            androidAbi = "test_android_abi_1",
                            pathToConfigIni = "test_path_to_config_ini_1",
                            emulatorStartOptions = emptyList(),
                            emulatorStartTimeoutSeconds = 180,
                            redirectLogcatTo = null
                    ),
                    Args(
                            emulatorName = "test_emulator_name_2",
                            pakage = "test_android_package_2",
                            androidAbi = "test_android_abi_2",
                            pathToConfigIni = "test_path_to_config_ini_2",
                            emulatorStartOptions = emptyList(),
                            emulatorStartTimeoutSeconds = 180,
                            redirectLogcatTo = null
                    )))
        }
    }
})
