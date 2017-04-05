package com.gojuno.janulator

import com.gojuno.cmd.common.*
import rx.Observable
import rx.schedulers.Schedulers
import rx.schedulers.Schedulers.io
import java.io.File
import java.lang.System.nanoTime
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit.*
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

val sh: String = "/bin/sh"
val avdManager: String = "$androidHome/tools/bin/avdmanager"
val emulator: String = "$androidHome/tools/emulator"

data class Emulator(
        val id: String,
        val name: String
)

fun startEmulator(args: Args, availablePortsSemaphore: Semaphore): Observable<Emulator> {
    return createAvd(args)
            .flatMap { applyConfig(args) }
            .map { availablePortsSemaphore.acquire() }
            .flatMap { findAvailablePortsForNewEmulator() }
            .doOnNext { log("Ports for emulator ${args.emulatorName}: ${it.first}, ${it.second}.") }
            .flatMap { ports ->
                val emulatorProcess = process(
                        // Unix only, PR welcome.
                        listOf(sh, "-c", "$emulator -verbose -avd ${args.emulatorName} -ports ${ports.first},${ports.second} ${args.emulatorStartOptions.joinToString(" ")} &"),
                        timeout = null,
                        redirectOutputTo = outputFileForEmulator(args)
                )
                waitForEmulatorToStart(args, emulatorProcess, ports)
            }
            .map { emulator -> availablePortsSemaphore.release().let { emulator } }
            .flatMap { emulator ->
                when (args.redirectLogcatTo) {
                    null -> Observable.just(emulator)
                    else -> {
                        val adbDevice = AdbDevice(id = emulator.id, online = true)
                        val logcatFile = File(args.redirectLogcatTo)

                        adbDevice
                                .redirectLogcatToFile(logcatFile)
                                .doOnSubscribe { adbDevice.log("Redirecting logcat output to file $logcatFile") }
                                .toObservable()
                                .subscribeOn(Schedulers.io())
                                .map { emulator }
                    }
                }
            }
            .flatMap { waitForEmulatorToFinishBoot(it) }
            .timeout(args.emulatorStartTimeoutSeconds, SECONDS)
            .doOnError {
                when (it) {
                    is TimeoutException -> println("Timeout ${args.emulatorStartTimeoutSeconds} seconds, failed to start emulator ${args.emulatorName}.")
                    else -> Unit
                }
            }
}

private fun createAvd(args: Args): Observable<Unit> {
    val createAvdProcess = process(
            listOf(
                    avdManager, "create",
                    "avd", "--force",
                    "--name", args.emulatorName,
                    "--package", args.pakage,
                    "--abi", args.androidAbi
            ),
            timeout = 60 to SECONDS
    ).share()

    val iDontWishToCreateCustomHardwareProfile = Observable
            .combineLatest(
                    createAvdProcess.filter { it is Notification.Start }.cast(Notification.Start::class.java),
                    Observable.interval(500, MILLISECONDS),
                    { notification, _ -> notification }
            )
            .first { it.output.readText().contains("Do you wish to create a custom hardware profile") }
            .observeOn(io())
            .map { it.process.outputStream.writer().use { it.write("no") } }

    val startTime = AtomicLong()

    return Observable
            .merge(iDontWishToCreateCustomHardwareProfile, createAvdProcess)
            .first { it is Notification.Exit }
            .doOnError { log("Error during creation of avd ${args.emulatorName}, error = $it") }
            .retry(3) // https://code.google.com/p/android/issues/detail?id=262719
            .map { Unit }
            .doOnSubscribe { log("Creating avd ${args.emulatorName}."); startTime.set(nanoTime()) }
            .doOnNext { log("Avd ${args.emulatorName} created in ${(nanoTime() - startTime.get()).nanosAsSeconds()} seconds.") }
            .doOnError { log("Could not create avd ${args.emulatorName}, error = $it") }
}

private fun applyConfig(args: Args): Observable<Unit> = Observable
        .fromCallable {
            File(args.pathToConfigIni)
                    .copyTo(File("$home/.android/avd/${args.emulatorName}.avd/config.ini"), overwrite = true)
        }
        .map { Unit }

private fun findAvailablePortsForNewEmulator(): Observable<Pair<Int, Int>> = connectedAdbDevices()
        .map { it.filter { it.isEmulator } }
        .map {
            if (it.isEmpty()) {
                5554
            } else {
                it
                        .map { it.id }
                        .map { it.substringAfter("emulator-") }
                        .map { it.toInt() }
                        .max()!!
                        .let { it + 2 }
            }
        }
        .map { it to it + 1 }

private fun waitForEmulatorToStart(args: Args, emulatorProcess: Observable<Notification>, ports: Pair<Int, Int>): Observable<Emulator> {
    val startTime = AtomicLong()

    return emulatorProcess
            .filter { it is Notification.Start }
            .cast(Notification.Start::class.java)
            .flatMap {
                // Wait for emulator serial number to show up in emulator -avd output.
                Observable
                        .interval(1000, MILLISECONDS)
                        .flatMap { connectedAdbDevices() }
                        .map {
                            if (it.map { it.id }.contains("emulator-${ports.first}")) {
                                Emulator(id = "emulator-${ports.first}", name = args.emulatorName)
                            } else {
                                null
                            }
                        }
                        .filter { it != null }
                        .first()
            }
            .cast(Emulator::class.java) // Lost Kotlin nullability after flatMap.
            .flatMap { emulator ->
                // Wait for emulator to show up in adb devices.
                Observable
                        .interval(1000, MILLISECONDS)
                        .flatMap { connectedAdbDevices() }
                        .map { it.firstOrNull { it.id == emulator.id } }
                        .filter { it != null }
                        .first()
                        .map { emulator }
            }
            .doOnSubscribe { log("Starting emulator ${args.emulatorName}."); startTime.set(nanoTime()) }
            .doOnNext { log("Emulator $it started in ${(nanoTime() - startTime.get()).nanosAsSeconds()} seconds.") }
            .doOnError { log("Error during start of the emulator ${args.emulatorName}, error = $it") }
}

private fun waitForEmulatorToFinishBoot(targetEmulator: Emulator): Observable<Emulator> {
    val startTime = AtomicLong()

    return Observable
            .interval(1500, MILLISECONDS)
            .switchMap { connectedAdbDevices() }
            .map { it.filter { it.isEmulator } }
            .switchMap { runningEmulators ->
                val emulator = runningEmulators.firstOrNull { it.id == targetEmulator.id }

                if (emulator == null || emulator.online == false) {
                    Observable.never()
                } else {
                    process(
                            listOf(
                                    adb,
                                    "-s", emulator.id,
                                    "shell",
                                    "getprop", "init.svc.bootanim"
                            ),
                            timeout = 10 to SECONDS
                    )
                            .filter { it is Notification.Exit }
                            .cast(Notification.Exit::class.java)
                            .map { it.output.readText().contains("stopped", ignoreCase = true) }
                            .switchMap { bootAnimationStopped ->
                                if (bootAnimationStopped) {
                                    Observable.just(targetEmulator)
                                } else {
                                    Observable.never()
                                }
                            }
                }
            }
            .first()
            .doOnSubscribe { log("Waiting boot process to finish for emulator $targetEmulator."); startTime.set(nanoTime()) }
            .doOnNext { log("Emulator $targetEmulator finished boot process in ${(nanoTime() - startTime.get()).nanosAsSeconds()} seconds.") }
            .doOnError { log("Error during start of the emulator $targetEmulator, error = $it.") }
}

fun Long.nanosAsSeconds(): Float = NANOSECONDS.toMillis(this) / 1000f

private fun outputFileForEmulator(args: Args) = File("${args.emulatorName}.output")
        .apply { deleteOnExit() }
