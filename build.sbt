import AssemblyKeys._ // put this at the top of the file

assemblySettings

name := "shelmet"

version := "1.0"

scalaVersion := "2.10.2"

resolvers += "spray repo" at "http://repo.spray.io"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies += "com.github.scopt" %% "scopt" % "3.1.0"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.13"

libraryDependencies += "junit" % "junit" % "4.8" % "test"

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test"

libraryDependencies += "xmlunit" % "xmlunit" % "1.4" % "test"

libraryDependencies += "com.typesafe" %% "scalalogging-slf4j" % "1.0.1"

libraryDependencies += "io.spray" % "spray-can" % "1.1-M8"

libraryDependencies += "io.spray" % "spray-routing" % "1.1-M8"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.1.4"

net.virtualvoid.sbt.graph.Plugin.graphSettings
