#!/bin/bash

set -e

docker build -t olvid-android .


echo 'Build successful, now cd.. and run:'
echo 'docker run --rm -v "$(pwd)":/olvid -w /olvid/obv_messenger --user "$(id -u):$(id -g)" olvid-android ./gradlew assembleProdNogoogleRelease'
