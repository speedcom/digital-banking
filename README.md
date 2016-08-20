# README

**Transaction engine for a stock exchange.**

In the next couple of paragraphs we are going to cover the technology of our choice and the motivation behind it.

## TOOLS


### Scala

We decided to deliver our solution in Scala (v. 2.11) mainly because of it's built in features, comprehensive syntax, good natural support for concurrent mechanisms, strong type system and support for functional programming.

We believe that those features allow developers to increase their productivity and make codebase maintenance less painful.

### Akka

In order not to reinvent the wheel and struggle with problems that are inherent to multithreaded programming we've chosen a technology that gives us developers higher level of abstraction.

Akka is a toolkit built on top of the Actor model with a promise to be super fast.
More info about it can be found [here](http://doc.akka.io/docs/akka/current/intro/what-is-akka.html).

### SBT

We've decided to use additional build tool in our stack-tech. Sbt is a well know tool that every Scala developer knows - it allows to build project, solve dependency, run code against tests. We personally prefer to use it over maven.
More info about it can be found [here](http://www.scala-sbt.org/).


## Performance

To grasp better information about performance of some part of the codebase we decided to use [JMH](http://openjdk.java.net/projects/code-tools/jmh/).

Task for running jmh is:

```
sbt jmh:run [options]
```

e.g. `sbt jmh:run -t1 -f 2 -wi 5 -i 10`

## Tests

We are seious about tests.
Besides standard pack of functional tests prepared by GFT team we decided to write our owns making the whole code coverage higher.

As a Scala testing framework we chose [ScalaTest](http://www.artima.com/scalatest/).

Task for running unit tests is:

```
sbt test
```

## Code Analysis

As developers we should strive to have our codebase to be the best as possible.
This means that quality and security of code is a necessity. Finding potential vulnerabilities, bugs and security threats ad-hoc is the way to go.

We use some great tools here in place:

1. [Scalastyle](http://www.scalastyle.org/) - scala style checker
2. [Scapegoat](https://github.com/sksamuel/sbt-scapegoat) - static code analysis library
3. [Scalafmt](http://scalafmt.org/) - code formatter
