import AssemblyKeys._ // put this at the top of the file

assemblySettings

name := "shelmet"

version := "1.0"

scalaVersion := "2.10.3"

resolvers += "spray repo" at "http://repo.spray.io"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies += "com.github.scopt" %% "scopt" % "3.2.0"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.13"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.0" % "test"

libraryDependencies += "xmlunit" % "xmlunit" % "1.5" % "test"

libraryDependencies += "com.typesafe" %% "scalalogging-slf4j" % "1.0.1"

libraryDependencies += "io.spray" % "spray-can" % "1.2.0"

libraryDependencies += "io.spray" % "spray-routing" % "1.2.0"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.2.3"

libraryDependencies += "com.typesafe.akka" % "akka-slf4j_2.10" % "2.2.3"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.2.3" % "test"

net.virtualvoid.sbt.graph.Plugin.graphSettings
