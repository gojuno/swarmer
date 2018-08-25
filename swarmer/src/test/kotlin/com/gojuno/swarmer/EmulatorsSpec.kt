package com.gojuno.swarmer

import com.gojuno.commander.android.AdbDevice
import com.gojuno.commander.android.adb
import com.gojuno.commander.os.Notification
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import rx.Completable
import rx.Observable
import rx.Single
import java.io.File
import java.util.concurrent.TimeUnit

class EmulatorsSpec : Spek({

    val ADB_DEVICES = setOf(
            AdbDevice("id1", online = true),
            AdbDevice("id2", online = true),
            AdbDevice("id3", online = true)
    )

    arrayOf(
            Commands.Stop(),
            Commands.Stop(timeoutSeconds = 10),
            Commands.Stop(timeoutSeconds = 0)
    ).forEach { command ->
        describe("emulator stop called with timeout ${command.timeoutSeconds}") {
            val connectedEmulators by memoized {
                { Single.just(ADB_DEVICES) }
            }
            val startProcess by memoized {
                mock<(List<String>, Pair<Int, TimeUnit>?) -> Completable>().apply {
                    whenever(invoke(any(), any())).thenReturn(Completable.complete())
                }
            }

            beforeEachTest {
                stopAllEmulators(
                        command,
                        connectedEmulators = connectedEmulators,
                        completableProcess = startProcess
                )
            }

            ADB_DEVICES.forEach { device ->
                it("should call stop command for emulator ${device.id}") {
                    verify(startProcess).invoke(
                            listOf(adb, "-s", device.id, "emu", "kill"),
                            command.timeoutSeconds to TimeUnit.SECONDS
                    )
                }
            }
        }
    }

    val START_COMMANDS = listOf(
            Commands.Start(
                    emulatorName = "emulator_1",
                    pakage = "system-images;android-25;google_apis;x86",
                    androidAbi = "google_apis/x86_64",
                    pathToConfigIni = "config.ini",
                    emulatorStartOptions = listOf("--no-window"),
                    emulatorStartTimeoutSeconds = 45L,
                    redirectOutputTo = "output-dir",
                    verbose = true,
                    keepOutputOnExit = true
            ),
            Commands.Start(
                    emulatorName = "emulator_2",
                    pakage = "system-images;android-25;google_apis;x86",
                    androidAbi = "google_apis/x86_64",
                    pathToConfigIni = "config2.ini",
                    emulatorStartOptions = listOf("--no-window"),
                    emulatorStartTimeoutSeconds = 45L,
                    redirectOutputTo = "output-dir",
                    verbose = true
            ),
            Commands.Start(
                    emulatorName = "emulator_3",
                    pakage = "system-images;android-25;google_apis;x86",
                    androidAbi = "google_apis/x86",
                    pathToConfigIni = "config3.ini",
                    emulatorStartOptions = listOf("--no-window --some option"),
                    emulatorStartTimeoutSeconds = 60L,
                    verbose = false,
                    keepOutputOnExit = true
            )
    )

    val EMULATOR_PORTS = Pair(12, 34)

    describe("start emulators command called") {
        val connectedAdbDevices by memoized {
            { Observable.just(emptySet<AdbDevice>()) }
        }

        val outputFile by memoized {
            File("")
        }

        val process by memoized {
            mock<Process>()
        }

        val createAvd by memoized {
            mock<(Commands.Start) -> Observable<Unit>>().apply {
                whenever(invoke(any())).thenReturn(Observable.just(Unit))
            }
        }

        val applyConfig by memoized {
            mock<(Commands.Start) -> Observable<Unit>>().apply {
                whenever(invoke(any())).thenReturn(Observable.just(Unit))
            }
        }

        val emulator by memoized {
            mock<(Commands.Start) -> String>().apply { whenever(invoke(any())).thenReturn("/path/to/emulator/binary") }
        }

        val startEmulatorsProcess by memoized {
            mock<(List<String>, Commands.Start) -> Observable<Notification>>().apply {
                whenever(invoke(any(), any())).thenReturn(Observable.just(
                        Notification.Start(process, outputFile),
                        Notification.Exit(outputFile)
                ))
            }
        }

        val waitForEmulatorToStart by memoized {
            val commandCaptor = argumentCaptor<Commands.Start>()

            mock<(Commands.Start, () -> Observable<Set<AdbDevice>>, Observable<Notification>, Pair<Int, Int>) -> Observable<Emulator>>().apply {
                whenever(invoke(commandCaptor.capture(), any(), any(), any())).thenAnswer {
                    Observable.just(
                            Emulator("emulator-${EMULATOR_PORTS.first}", commandCaptor.firstValue.emulatorName)
                    )
                }
            }
        }

        val waitForEmulatorToFinishBoot by memoized {
            val emulatorCaptor = argumentCaptor<Emulator>()

            mock<(Emulator, Commands.Start) -> Observable<Emulator>>().apply {
                whenever(invoke(emulatorCaptor.capture(), any())).thenAnswer {
                    Observable.just(emulatorCaptor.firstValue)
                }
            }
        }

        val findAvailablePortsForNewEmulator by memoized {
            mock<() -> Observable<Pair<Int, Int>>>().apply {
                whenever(invoke()).thenReturn(Observable.just(EMULATOR_PORTS))
            }
        }

        beforeEachTest {
            startEmulators(
                    args = START_COMMANDS,
                    connectedAdbDevices = connectedAdbDevices,
                    createAvd = createAvd,
                    applyConfig = applyConfig,
                    emulator = emulator,
                    startEmulatorProcess = startEmulatorsProcess,
                    waitForEmulatorToStart = waitForEmulatorToStart,
                    findAvailablePortsForNewEmulator = findAvailablePortsForNewEmulator,
                    waitForEmulatorToFinishBoot = waitForEmulatorToFinishBoot
            )
        }

        START_COMMANDS.forEach { command ->
            it("should start emulators") {
                verify(startEmulatorsProcess).invoke(
                        sh +
                        listOf(
                                "${emulator(command)} ${if (command.verbose) "-verbose" else ""} -avd ${command.emulatorName} -ports ${EMULATOR_PORTS.first},${EMULATOR_PORTS.second} ${command.emulatorStartOptions.joinToString(" ")} $runInBackground".trim()
                        ),
                        command
                )
            }
        }
    }
})