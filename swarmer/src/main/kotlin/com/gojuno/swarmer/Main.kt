package com.gojuno.swarmer

import com.gojuno.commander.android.connectedAdbDevices
import com.gojuno.commander.os.log
import rx.Observable
import rx.schedulers.Schedulers.io
import java.lang.System.nanoTime
import java.util.concurrent.Semaphore

fun main(vararg rawArgs: String) {
    val args = parseArgs(rawArgs.toList())

    val startTime = nanoTime()

    // Sometimes on Linux "emulator -verbose -avd" does not print serial id of started emulator,
    // so by allocating ports manually we know which serial id emulator will have.
    val availablePortsSemaphore = Semaphore(1)

    val startedEmulators = connectedAdbDevices()
            .doOnNext { log("Already running emulators: $it") }
            .flatMap {
                val startEmulators: List<Observable<Emulator>> = args
                        .map { startEmulator(it, availablePortsSemaphore) }
                        .map { it.subscribeOn(io()) } // So each emulator will start in parallel.
                        .map { it.doOnNext { log("Emulator $it is ready.") } }

                Observable.zip(startEmulators, { startedEmulator -> startedEmulator })
            }
            .map { it.map { it as Emulator }.toSet() }
            .toBlocking()
            .firstOrDefault(emptySet())

    log("Swarmer: - \"My job is done here, took ${(nanoTime() - startTime).nanosAsSeconds()} seconds, startedEmulators: $startedEmulators, bye bye.\"")
    System.exit(0) // Force exit, emulator and logcat redirect will keep running as detached processes.
}
