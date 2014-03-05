name := "shelmet"

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.3"

//scalacOptions ++= Seq("-Xplugin:/workspace/recode/recode-compiler-plugin/target/scala-2.10/recode-compiler-plugin_2.10-1.0.jar")

resolvers ++= Seq(
  Resolver.mavenLocal,
  Resolver.sonatypeRepo("snapshots"),
  Resolver.typesafeRepo("releases"),
  "spray repo" at "http://repo.spray.io"
)

libraryDependencies ++= List(
  "com.github.scopt" %% "scopt" % "3.2.0",
  "ch.qos.logback" % "logback-classic" % "1.0.13",
  "org.scalatest" %% "scalatest" % "2.0" % "test",
  "xmlunit" % "xmlunit" % "1.5" % "test",
  "junit" % "junit" % "4.11" % "test",
  "com.typesafe" %% "scalalogging-slf4j" % "1.0.1",
  "io.spray" % "spray-can" % "1.2.0" exclude("org.scala-lang", "scala-library"),
  "io.spray" % "spray-routing" % "1.2.0" exclude("org.scala-lang", "scala-library"),
  "com.googlecode.matrix-toolkits-java" % "mtj" % "1.0.2-SNAPSHOT" intransitive(),
  "com.github.fommil" %% "debox" % "0.3.0-SNAPSHOT",
  "com.netflix.rxjava" % "rxjava-scala" % "0.16.1" intransitive(),
  "com.netflix.rxjava" % "rxjava-core" % "0.16.1" intransitive(),
  "net.liftweb" %% "lift-json" % "2.5.1",
  "com.google.guava" % "guava" % "16.0",
  "org.mapdb" % "mapdb" % "0.9.9",
//  "net.sf.trove4j" % "trove4j" % "3.0.3",
  "com.typesafe.akka" %% "akka-actor" % "2.2.3",
  "com.typesafe.akka" % "akka-slf4j_2.10" % "2.2.3",
  "com.typesafe.akka" %% "akka-testkit" % "2.2.3" % "test",
  "org.specs2" %% "specs2" % "2.2.3" % "test"
)

ideaExcludeFolders ++= Seq(".idea", ".idea_modules")

net.virtualvoid.sbt.graph.Plugin.graphSettings
