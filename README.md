# README

Transaction engine for a stock exchange.

## Scala

We decided to deliver our solution in Scala (v. 2.11) mainly because of its built in features, comprehensive syntax, good natural support for concurrent mechanisms, strong type system and support for functional programming.

We believe that such possibilities allow developer to increase their daily job and make codebase maintenance to be less painful.

## Akka

As a developer we should strive to use solution with higher level of abstraction to simply not play with quirkies that are guaranteed by using the primitives.
Its really hard to manage multihreaded applications.

Akka is a toolkit built on top of Actor model with promise to be super fast.
More info can be found [here](http://doc.akka.io/docs/akka/current/intro/what-is-akka.html).

## SBT

We decided to use additional built tool in our stack-tech. Sbt is a well know tool that every Scala developer know - it allows to build project, solve dependency, run code against tests. We personally prefer to use it over maven.
More info can be found [here](http://www.scala-sbt.org/).

## Tests

Besides standard pack of functional and performance tests prepared by guys from GFT (we love them!) we decided to write our owns making whole code coverage higher.

As a Scala testing framework we chose [ScalaTest](http://www.artima.com/scalatest/).

Task for running test is:

```
sbt test
```

## Performance

To grasp better information about performance of some part of the codebase we decided to use [JMH](http://openjdk.java.net/projects/code-tools/jmh/).

Task for running jmh is:

```
sbt jmh:run [options]
```

e.g. `sbt jmh:run -t1 -f 2 -wi 5 -i 10`

## Code Analysis

As a developer we should strive to have our codebase to be the best as possible.
This means that quality and security of code is a necessity. Finding potential vulnerabilities, bugs and security threats ad-hoc is a way to go. 

We use some great tools in place:

1. [Scalastyle](http://www.scalastyle.org/) - scala style checker
2. [Scapegoat](https://github.com/sksamuel/sbt-scapegoat) - static code analysis library
3. [Scalafmt](http://scalafmt.org/) - code formatter for Scala 
