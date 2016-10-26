#!/bin/bash

export publish_cmd="publishLocal"

if [[ $TRAVIS_PULL_REQUEST == "false" && $TRAVIS_BRANCH == "master" && $(cat version.sbt) =~ "-SNAPSHOT" ]]; then
  export publish_cmd="publish gitSnapshots publish"
fi

sbt_cmd="sbt ++$TRAVIS_SCALA_VERSION"

jvm="$sbt_cmd coverage validate coverageReport coverageAggregate && codecov"

run_cmd="$jvm && $sbt_cmd $publish_cmd"

eval $run_cmd
