---
layout: home
technologies:
 - first: ["Scala", "akka-d3 plugin is completely written in Scala"]
 - second: ["Akka", "akka-d3 is built on top of akka"]
---

## About
**Akka-d3** is a library to help you with Domain Driven Design, possibly in a distributed environment, on top of Akka. 
It does so by embracing event sourcing and CQRS.

## Getting started

Akka-d3 is currently available for Scala 2.11 & 2.12. The library is published on maven central, and can be added to your project,
by adding the following line to your build configuration (e.g. `build.sbt`).

```tut:evaluated
println(s"""libraryDependencies += "io.pjan" %% "akka-d3" % ${akka.contrib.d3.BuildInfo.lastTag}""")
```

This will pull in all of akka-d3 core & cluster modules. If you only require some functionality,
or you want additional functionality, you can pick-and-choose from amongst these modules (used in place of `akka-d3`):

 * `akka-d3-core`: Core of akka-d3. Allows you to run the write side in single-node mode (*required*).
 * `akka-d3-cluster`: Module to extend the write side of akka-d3 so aggregates can be sharded over multiple nodes.
 * `akka-d3-query-inmemory`: In-memory query backend, using [akka-persistence-inmemory](https://github.com/dnvriend/akka-persistence-inmemory).
 * `akka-d3-query-cassandra`: Cassandra query backend, using [akka-persistence-cassandra](https://github.com/akka/akka-persistence-cassandra).
 * `akka-d3-readside-cassandra`: Readside extension for cassandra.

## More info

Head over to some [background introduction](docs/intro), 

## Copyright and licence
All code is available to you under the MIT license, available at
http://opensource.org/licenses/mit-license.php and also in the
[COPYING](https://github.com/pjan/akka-d3/blob/master/COPYING) file.
Concepts and design is informed by other projects.
