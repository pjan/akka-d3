## akka-d3

[![Build Status](https://travis-ci.org/pjan/akka-d3.svg?branch=master)](https://travis-ci.org/pjan/akka-d3)
[![Chat](https://badges.gitter.im/pjan/akka-d3.svg)](https://gitter.im/pjan/akka-d3?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![codecov](https://codecov.io/gh/pjan/akka-d3/branch/master/graph/badge.svg)](https://codecov.io/gh/pjan/akka-d3)

### Overview

Akka-d3 is a library which provides abstractions for doing Domain Driven Design using Event Sourcing and CQRS, possibly in a distributed way. It is built on top of Akka, and works as an extension to it.

### Getting Started

Akka-d3 is currently available for 2.11.

To get started with SBT, simply add the following to your `build.sbt` file:

```Scala
libraryDependencies += "io.pjan" %% "akka-d3" % "0.1.0"
```

This will pull in all of Akka-d3's modules. If you only require some functionality, you can pick-and-choose from amongst these modules (used in place of "akka-d3"):

 * `akka-d3-core`: Core of akka-d3. Allows you to run the write side in single-node mode (*required*).
 * `akka-d3-cluster`: Module to extend the write side of akka-d3 so aggregates can be sharded over multiple nodes.

-

### Documentation

### Copyright and License
All code is available to you under the MIT license, available at
http://opensource.org/licenses/mit-license.php and also in the
[COPYING](COPYING) file. Concepts and design is informed by other projects.

Copyright the maintainers, 2016.
