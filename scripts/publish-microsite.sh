#!/bin/bash
set -e

git config --global user.email "pjan@pjan.io"
git config --global user.name "pjan vandaele"
git config --global push.default simple

sbt docs/publishMicrosite
