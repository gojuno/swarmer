package com.gojuno.swarmer

import com.gojuno.commander.android.AdbDevice
import com.gojuno.commander.android.adb
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.mockito.Mockito
import rx.Completable
import rx.Single
import java.util.concurrent.TimeUnit

class EmulatorsSpec : Spek({

    val ADB_DEVICES = setOf(
            AdbDevice("id1", true),
            AdbDevice("id2", true),
            AdbDevice("id3", true)
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
                    Mockito.verify(startProcess).invoke(
                            listOf(adb, "-s", device.id, "emu", "kill"),
                            command.timeoutSeconds to TimeUnit.SECONDS
                    )
                }
            }
        }
    }
})