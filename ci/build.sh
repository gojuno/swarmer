#!/bin/bash
set -xe

# You can run it from any directory.
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_DIR="$DIR/.."

pushd "$PROJECT_DIR"

USER_ID=`id -u $USER`

BUILD_COMMAND="set -xe && "
BUILD_COMMAND+="apt-get update && apt-get -y install sudo && "
BUILD_COMMAND+="groupadd --gid $USER_ID build_user && "
BUILD_COMMAND+="useradd --shell /bin/bash --uid $USER_ID --gid $USER_ID --create-home build_user && "
BUILD_COMMAND+="sudo --set-home --preserve-env -u build_user "

BUILD_COMMAND+="/opt/project/gradlew "
BUILD_COMMAND+="--no-daemon --info "
BUILD_COMMAND+="clean build "

if [ "$PUBLISH" = "true" ]; then
    BUILD_COMMAND+="artifactoryPublish "
fi

BUILD_COMMAND+="--project-dir /opt/project"

docker run \
-v `"pwd"`:/opt/project \
openjdk:8u121-jdk \
bash -c "$BUILD_COMMAND"

popd
