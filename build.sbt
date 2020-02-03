ThisBuild / organization := "zone.slice"
ThisBuild / organizationHomepage := Some(url("https://slice.zone"))
ThisBuild / scalaVersion := "2.13.1"
ThisBuild / version := "0.0.0"

lazy val root = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "chaos",
    description := "A purely functional Discord build scraper.",
    startYear := Some(2020),
    homepage := Some(url("https://github.com/slice/chaos")),
    developers := List(
      Developer("slice", "slice", "slice@slice.zone", url("https://slice.zone"))
    ),
    resolvers += Resolver.mavenCentral,
    resolvers += Resolver.sonatypeRepo("snapshots"),
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-blaze-client" % "0.21.0-SNAPSHOT",
      "org.http4s" %% "http4s-dsl" % "0.21.0-SNAPSHOT",
      "org.typelevel" %% "cats-core" % "2.1.0",
      "org.typelevel" %% "cats-effect" % "2.1.0",
      "co.fs2" %% "fs2-core" % "2.2.1",
      "io.chrisdavenport" %% "log4cats-core" % "1.0.1",
      "io.chrisdavenport" %% "log4cats-slf4j" % "1.0.1",
      "ch.qos.logback" % "logback-classic" % "1.2.3"
    ),
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, homepage),
    buildInfoPackage := "zone.slice.chaos"
  )
