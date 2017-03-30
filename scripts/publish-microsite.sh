#!/bin/bash
set -e

git config --global user.email "travis@pjan.io"
git config --global user.name "pjan (travis)"
git config --global push.default simple

sbt docs/publishMicrosite
