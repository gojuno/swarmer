package com.gojuno.swarmer

import com.gojuno.commander.android.*
import com.gojuno.commander.os.*
import rx.Completable
import rx.Observable
import rx.Single
import rx.schedulers.Schedulers
import rx.schedulers.Schedulers.io
import java.io.File
import java.lang.System.nanoTime
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.*
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

val sh: List<String> = when (os()) {
    Os.Linux, Os.Mac -> listOf("/bin/sh", "-c")
    Os.Windows -> listOf("cmd", "/C")
}
val runInBackground: String = when (os()) {
    Os.Linux, Os.Mac -> "&"
    Os.Windows -> ""
}
val avdManager: String = when (os()) {
    Os.Linux, Os.Mac -> "$androidHome/tools/bin/avdmanager"
    Os.Windows -> "$androidHome/tools/bin/avdmanager.bat"
}
val emulator = when (os()) {
    Os.Linux, Os.Mac -> "$androidHome/emulator/emulator"
    Os.Windows -> "$androidHome/emulator/emulator.exe"
}
val emulatorCompat = when (os()) {
    Os.Linux, Os.Mac -> "$androidHome/tools/emulator"
    Os.Windows -> "$androidHome/tools/emulator.exe"
}

data class Emulator(
        val id: String,
        val name: String
)

fun startEmulators(
        args: List<Commands.Start>,
        connectedAdbDevices: () -> Observable<Set<AdbDevice>> = ::connectedAdbDevices,
        createAvd: (args: Commands.Start) -> Observable<Unit> = ::createAvd,
        applyConfig: (args: Commands.Start) -> Observable<Unit> = ::applyConfig,
        emulator: (args: Commands.Start) -> String = ::emulatorBinary,
        findAvailablePortsForNewEmulator: () -> Observable<Pair<Int, Int>> = ::findAvailablePortsForNewEmulator,
        startEmulatorProcess: (List<String>, Commands.Start) -> Observable<Notification> = ::startEmulatorProcess,
        waitForEmulatorToStart: (Commands.Start, () -> Observable<Set<AdbDevice>>, Observable<Notification>, Pair<Int, Int>) -> Observable<Emulator> = ::waitForEmulatorToStart,
        waitForEmulatorToFinishBoot: (Emulator, Commands.Start) -> Observable<Emulator> = ::waitForEmulatorToFinishBoot
) {
    val startTime = System.nanoTime()

    // Sometimes on Linux "emulator -verbose -avd" does not print serial id of started emulator,
    // so by allocating ports manually we know which serial id emulator will have.
    val availablePortsSemaphore = Semaphore(1)

    val startedEmulators = connectedAdbDevices()
            .doOnNext { log("Already running emulators: $it") }
            .flatMap {
                val startEmulators: List<Observable<Emulator>> = args
                        .map { command ->
                            startEmulator(
                                    args = command,
                                    createAvd = createAvd,
                                    applyConfig = applyConfig,
                                    availablePortsSemaphore = availablePortsSemaphore,
                                    findAvailablePortsForNewEmulator = findAvailablePortsForNewEmulator,
                                    startEmulatorProcess = startEmulatorProcess,
                                    waitForEmulatorToStart = waitForEmulatorToStart,
                                    connectedAdbDevices = connectedAdbDevices,
                                    emulator = emulator,
                                    waitForEmulatorToFinishBoot = waitForEmulatorToFinishBoot
                            )
                        }
                        .map { it.subscribeOn(Schedulers.io()) } // So each emulator will start in parallel.
                        .map { it.doOnNext { log("Emulator $it is ready.") } }

                Observable.zip(startEmulators, { startedEmulator -> startedEmulator })
            }
            .map { it.map { it as Emulator }.toSet() }
            .toBlocking()
            .firstOrDefault(emptySet())

    log("Swarmer: - \"My job is done here, took ${(System.nanoTime() - startTime).nanosAsSeconds()} seconds, startedEmulators: $startedEmulators, bye bye.\"")
}

private fun startEmulatorProcess(args: List<String>, command: Commands.Start) =
        process(
                commandAndArgs = args,
                timeout = null,
                redirectOutputTo = outputFileForEmulator(command),
                keepOutputOnExit = command.keepOutputOnExit
        )

private fun startEmulator(
        args: Commands.Start,
        createAvd: (args: Commands.Start) -> Observable<Unit>,
        applyConfig: (args: Commands.Start) -> Observable<Unit>,
        availablePortsSemaphore: Semaphore,
        findAvailablePortsForNewEmulator: () -> Observable<Pair<Int, Int>>,
        startEmulatorProcess: (List<String>, Commands.Start) -> Observable<Notification>,
        waitForEmulatorToStart: (Commands.Start, () -> Observable<Set<AdbDevice>>, Observable<Notification>, Pair<Int, Int>) -> Observable<Emulator>,
        connectedAdbDevices: () -> Observable<Set<AdbDevice>> = ::connectedAdbDevices,
        emulator: (Commands.Start) -> String,
        waitForEmulatorToFinishBoot: (Emulator, Commands.Start) -> Observable<Emulator>
): Observable<Emulator> =
        createAvd(args)
                .flatMap { applyConfig(args) }
                .map { availablePortsSemaphore.acquire() }
                .flatMap { findAvailablePortsForNewEmulator() }
                .doOnNext { log("Ports for emulator ${args.emulatorName}: ${it.first}, ${it.second}.") }
                .flatMap { ports ->
                    startEmulatorProcess(
                            sh + "${emulator(args)} ${if (args.verbose) "-verbose " else ""}-avd ${args.emulatorName} -ports ${ports.first},${ports.second} ${args.emulatorStartOptions.joinToString(" ")} $runInBackground".trim(),
                            args
                    ).let { process ->
                        waitForEmulatorToStart(args, connectedAdbDevices, process, ports)
                    }
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
                .flatMap { emulator ->
                    waitForEmulatorToFinishBoot(emulator, args)
                }
                .timeout(args.emulatorStartTimeoutSeconds, SECONDS)
                .doOnError {
                    when (it) {
                        is TimeoutException -> println("Timeout ${args.emulatorStartTimeoutSeconds} seconds, failed to start emulator ${args.emulatorName}.")
                        else -> Unit
                    }
                }

fun stopAllEmulators(
        args: Commands.Stop,
        connectedEmulators: () -> Single<Set<AdbDevice>> = ::connectedEmulators,
        completableProcess: (List<String>, Pair<Int, TimeUnit>?) -> Completable = ::completableProcess
) {
    val startTime = System.nanoTime()

    connectedEmulators()
            .map { emulators ->
                log("Stopping running emulators: $emulators.")
                emulators.map { emulator ->
                    completableProcess(
                            listOf(adb, "-s", emulator.id, "emu", "kill"),
                            args.timeoutSeconds to SECONDS
                    )
                            .doOnCompleted { log("Stopped emulator $emulator.") }
                }
            }
            .flatMapCompletable(Completable::merge)
            .doOnError { log("Error during stopping emulators, error = $it.") }
            .doOnCompleted { log("All emulators stopped.") }
            .await()

    log("Swarmer: - \"My job is done here, took ${(System.nanoTime() - startTime).nanosAsSeconds()} seconds, bye bye.\"")
}

private fun completableProcess(args: List<String>, timeout: Pair<Int, TimeUnit>?) =
        process(args, timeout)
                .filter { it is Notification.Exit }
                .toCompletable()

private fun createAvd(args: Commands.Start): Observable<Unit> {
    val createAvdProcess = process(
            listOf(
                    avdManager, "create",
                    "avd", "--force",
                    "--name", args.emulatorName,
                    "--package", args.pakage,
                    "--abi", args.androidAbi
            ),
            timeout = 60 to SECONDS,
            redirectOutputTo = outputDirectory(args),
            keepOutputOnExit = args.keepOutputOnExit
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

private fun applyConfig(args: Commands.Start): Observable<Unit> = Observable
        .fromCallable {
            File(args.pathToConfigIni)
                    .copyTo(File("$home/.android/avd/${args.emulatorName}.avd/config.ini"), overwrite = true)
        }
        .map { Unit }

private fun emulatorBinary(args: Commands.Start): String =
        if (args.useCompatEmulator) {
            emulatorCompat
        } else {
            emulator
        }

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
                        .max()!! + 2
            }
        }
        .map { it to it + 1 }

private fun waitForEmulatorToStart(
        args: Commands.Start,
        connectedAdbDevices: () -> Observable<Set<AdbDevice>>,
        emulatorProcess: Observable<Notification>,
        ports: Pair<Int, Int>
): Observable<Emulator> {
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

private fun waitForEmulatorToFinishBoot(
        targetEmulator: Emulator,
        args: Commands.Start
): Observable<Emulator> {
    val startTime = AtomicLong()

    return Observable
            .interval(1500, MILLISECONDS)
            .switchMap { connectedEmulators().toObservable() }
            .switchMap { runningEmulators ->
                val emulator = runningEmulators.firstOrNull { it.id == targetEmulator.id }

                if (emulator == null || !emulator.online) {
                    Observable.never()
                } else {
                    process(
                            listOf(
                                    adb,
                                    "-s", emulator.id,
                                    "shell",
                                    "getprop", "init.svc.bootanim"
                            ),
                            timeout = 10 to SECONDS,
                            redirectOutputTo = outputDirectory(args),
                            keepOutputOnExit = args.keepOutputOnExit
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

private fun Long.nanosAsSeconds(): Float = NANOSECONDS.toMillis(this) / 1000f

private fun outputFileForEmulator(args: Commands.Start) =
        File(outputDirectory(args), "${args.emulatorName}.output").apply {
            if (!args.keepOutputOnExit) deleteOnExit()
        }

private fun outputDirectory(args: Commands.Start) =
        args.redirectOutputTo?.run {
            File(this).apply { mkdirs() }
        }

private fun connectedEmulators(): Single<Set<AdbDevice>> =
        connectedAdbDevices().take(1).toSingle().map { it.filter { it.isEmulator }.toSet() }

private fun os(): Os {
    val os = System.getProperty("os.name", "unknown").toLowerCase(Locale.ENGLISH)

    return if (os.contains("mac") || os.contains("darwin")) {
        Os.Mac
    } else if (os.contains("linux")) {
        Os.Linux
    } else if (os.contains("windows")) {
        Os.Windows
    } else {
        throw IllegalStateException("Unsupported os $os, only ${Os.values()} are supported.")
    }
}
