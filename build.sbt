inThisBuild(
  Seq(
    organization := "zone.slice",
    scalaVersion := "3.0.1",
    startYear := Some(2021),
  ),
)

val V = new {
  val fs2    = "3.1.1"
  val http4s = "0.23.1"
}

lazy val root = (project in file(".")).settings(
  name := "chaos",
  version := "0.0.0",
  fork := true,
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-effect"         % "3.2.5",
    "co.fs2"        %% "fs2-core"            % V.fs2,
    "co.fs2"        %% "fs2-io"              % V.fs2,
    "org.typelevel" %% "munit-cats-effect-3" % "1.0.5" % Test,
    "org.http4s"    %% "http4s-dsl"          % V.http4s,
    "org.http4s"    %% "http4s-blaze-client" % V.http4s,
  ),
)
