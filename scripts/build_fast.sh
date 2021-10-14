#!/bin/env bash

./gradlew shadowJar
docker build -t nikkyai/discordbot:latest -f local.Dockerfile .
