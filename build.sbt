val catsVersion = "2.1.0"
val http4sVersion = "0.21.0-SNAPSHOT"
val fs2Version = "2.2.1"

lazy val root = (project in file(".")).settings(
  name := "chaos",
  version := "0.0",
  description := "A purely functional Discord build scraper.",
  startYear := Some(2020),
  homepage := Some(url("https://github.com/slice/chaos")),
  developers := List(
    Developer("slice", "slice", "slice@slice.zone", url("https://slice.zone"))
  ),
  scalaVersion := "2.13.1",
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-core",
    "org.typelevel" %% "cats-effect"
  ).map(_ % catsVersion),
  resolvers += Resolver.sonatypeRepo("snapshots"),
  libraryDependencies ++= Seq(
    "org.http4s" %% "http4s-blaze-client"
  ).map(_ % http4sVersion),
  libraryDependencies ++= Seq(
    "co.fs2" %% "fs2-core"
  ).map(_ % fs2Version)
)
