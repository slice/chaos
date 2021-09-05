inThisBuild(
  Seq(
    organization := "zone.slice",
    scalaVersion := "2.13.6",
    startYear    := Some(2021),
  ),
)

val V = new {
  val cats   = "2.6.1"
  val fs2    = "3.1.1"
  val http4s = "0.23.1"
  val circe  = "0.14.1"
}

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")

lazy val chaos = (project in file(".")).settings(
  name    := "chaos",
  version := "0.0.0",
  fork    := true,
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-core"           % V.cats,
    "org.typelevel" %% "cats-effect"         % "3.2.5",
    "co.fs2"        %% "fs2-core"            % V.fs2,
    "co.fs2"        %% "fs2-io"              % V.fs2,
    "org.typelevel" %% "munit-cats-effect-3" % "1.0.5" % Test,
    "org.http4s"    %% "http4s-dsl"          % V.http4s,
    "org.http4s"    %% "http4s-blaze-client" % V.http4s,
    "org.http4s"    %% "http4s-circe"        % V.http4s,
    "io.circe"      %% "circe-core"          % V.circe,
    "io.circe"      %% "circe-parser"        % V.circe,
    "io.circe"      %% "circe-literal"       % V.circe,
  ),

  // better-monadic-for enables useful implicit patterns, but the compiler
  // marks the values as unused even when they actually are being used.
  //
  // Relevant: https://github.com/oleg-py/better-monadic-for/issues/50
  scalacOptions += "-Wconf:msg=\\$implicit\\$:s",
)
