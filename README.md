## akka-d3

[![Build Status](https://travis-ci.org/pjan/akka-d3.svg?branch=master)](https://travis-ci.org/pjan/akka-d3)
[![Chat](https://badges.gitter.im/pjan/akka-d3.svg)](https://gitter.im/pjan/akka-d3?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![codecov](https://codecov.io/gh/pjan/akka-d3/branch/master/graph/badge.svg)](https://codecov.io/gh/pjan/akka-d3)

### Overview

*akka-d3* is a library to help you with Domain Driven Design, possibly in a distributed environment, on top of Akka. 
It does so by embracing event sourcing and CQRS. More info & documentation can be found [here](https://pjan.github.io/akka-d3/).

### Dependencies

*akka-d3* relies on the `protobuf` cli (version 2.5.0) being in the system path.
To confirm if the correct version of `protobuf` is installed on your system, type: `protoc --version`.
Once `protobuf` is available, start an `sbt` shell and run `protobufGenerate` (for more details, see https://github.com/sbt/sbt-protobuf#tasks).
Now, the code should `compile`.

### Copyright and License
All code is available to you under the MIT license, available at
http://opensource.org/licenses/mit-license.php and also in the
[COPYING](COPYING) file. Concepts and design is informed by other projects.

Copyright the maintainers, 2016-2017.
