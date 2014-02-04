import AssemblyKeys._ // put this at the top of the file

assemblySettings

name := "shelmet"

version := "1.0"

scalaVersion := "2.10.3"

//scalacOptions ++= Seq("-Xplugin:/workspace/recode/recode-compiler-plugin/target/scala-2.10/recode-compiler-plugin_2.10-1.0.jar")

resolvers ++= Seq(
  Resolver.mavenLocal,
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  Resolver.typesafeRepo("releases"),
  "spray repo" at "http://repo.spray.io"
)

libraryDependencies += "com.github.scopt" %% "scopt" % "3.2.0"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.13"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.0" % "test"

libraryDependencies += "xmlunit" % "xmlunit" % "1.5" % "test"

libraryDependencies += "com.typesafe" %% "scalalogging-slf4j" % "1.0.1"

libraryDependencies += "io.spray" % "spray-can" % "1.2.0"

libraryDependencies += "io.spray" % "spray-routing" % "1.2.0"

// we don't touch the netlib-java parts
libraryDependencies += "com.googlecode.matrix-toolkits-java" % "mtj" % "1.0.2-SNAPSHOT" intransitive()

libraryDependencies += "com.google.guava" % "guava" % "16.0"

libraryDependencies += "net.sf.trove4j" % "trove4j" % "3.0.3"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.2.3"

libraryDependencies += "com.typesafe.akka" % "akka-slf4j_2.10" % "2.2.3"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.2.3" % "test"

libraryDependencies += "org.specs2" %% "specs2" % "2.2.3" % "test"

net.virtualvoid.sbt.graph.Plugin.graphSettings
