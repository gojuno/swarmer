## Swarmer — create, start and wait for Android emulators to boot in parallel.

`Swarmer` is a replacement for [such bash scripts](https://github.com/travis-ci/travis-cookbooks/blob/master/community-cookbooks/android-sdk/files/default/android-wait-for-emulator) but with features like:

* Start **multiple** Android Emulators and wait for each to boot in parallel.
* Pass `config.ini` that'll be applied to created emulator.
* Redirect Logcat output of an emulator to a file.

![Demo](swarmer/swarmer.gif)

## How to use

`Swarmer` shipped as `jar`, so just run it `java -jar swarmer.jar options`.

Dependencies:

* JVM 1.8+
* [Android SDK Tools 26.0.0+](https://developer.android.com/studio/releases/sdk-tools.html)

#### Supported options

##### Required

* `--emulator-name`
  * Name of the emulator, i.e. `test_emulator_1`.
* `--package`
  * Package of the system image for this AVD (e.g.'system-images;android-25;google_apis;x86') to pass to `avdmanager create avd --package`.
* `--android-abi`
  * Android system image abi, i.e. `google_apis/x86_64`.
* `--path-to-config-ini`
  * Path either relative or absolute to the file that will be used as `config.ini` for created emulator.

##### Optional

* `--help, -help, help, -h`
  * Print help and exit.
* `--emulator-start-options`
  * Options to pass to `emulator -avd \$emulatorName` command, i.e. `--no-window -prop persist.sys.language=en -prop persist.sys.country=US`.
* `--emulator-start-timeout-seconds`
  * Timeout to wait for emulator to finish boot. Default value is 180 seconds.
* `--redirect-logcat-to`
  * Path either relative or absolute to the file that will be used to redirect logcat of started emulator to. No redirection will happen if parameter is not presented.

##### Examples

###### Start one emulator

```console
java -jar swarmer.jar \
--emulator-name test_emulator_1 \
--package "system-images;android-25;google_apis;x86" \
--android-abi google_apis/x86_64 \
--path-to-config-ini emulator_config.ini \
--emulator-start-options -prop persist.sys.language=en -prop persist.sys.country=US \
--redirect-logcat-to test_emulator_1_logcat.txt
```

###### Start two emulators in parallel

```console
java -jar swarmer.jar \
--emulator-name test_emulator_1 \
--package "system-images;android-25;google_apis;x86" \
--android-abi google_apis/x86_64 \
--path-to-config-ini emulator_config1.ini \
--emulator-start-options -prop persist.sys.language=en -prop persist.sys.country=US \
--redirect-logcat-to test_emulator_1_logcat.txt
--emulator-name test_emulator_2 \
--package "system-images;android-23;google_apis;x86" \
--android-abi google_apis/x86_64 \
--path-to-config-ini emulator_config2.ini \
--emulator-start-options -prop persist.sys.language=en -prop persist.sys.country=US \
--redirect-logcat-to test_emulator_2_logcat.txt
```

###### Start two emulators sequentially

```console
java -jar swarmer.jar \
--emulator-name test_emulator_1 \
--android-target android-25 \
--android-abi google_apis/x86_64 \
--path-to-config-ini emulator_config.ini \
--emulator-start-options -prop persist.sys.language=en -prop persist.sys.country=US \
--redirect-logcat-to test_emulator_1_logcat.txt

java -jar swarmer.jar \
--emulator-name test_emulator_2 \
--package "system-images;android-23;google_apis;x86" \
--android-abi google_apis/x86_64 \
--path-to-config-ini emulator_config2.ini \
--emulator-start-options -prop persist.sys.language=en -prop persist.sys.country=US \
--redirect-logcat-to test_emulator_2_logcat.txt
```

### Download

Swarmer is [available on jcenter](https://jcenter.bintray.com/com/gojuno/swarmer).

>You can download it in your CI scripts or store it in your version control system (not recommended).

```console
SWARMER_VERSION=some-version
curl --fail --location https://jcenter.bintray.com/com/gojuno/swarmer/swarmer/${SWARMER_VERSION}/swarmer-${SWARMER_VERSION}.jar --output /tmp/swarmer.jar
```

All the releases and changelogs can be found on [Releases Page](https://github.com/gojuno/swarmer/releases).

### How to build

Dependencies: you only need `docker` and `bash` installed on your machine.

```console
bash ci/build.sh
```

## License

```
Copyright 2017 Juno, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
