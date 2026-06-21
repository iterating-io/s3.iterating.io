#!/bin/bash

set -e

set -a
source .env
set +a

./gradlew :nats:localRun