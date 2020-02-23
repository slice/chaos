// format: off
inThisBuild(Seq(
    organization := "zone.slice",
    organizationHomepage := Some(url("https://slice.zone")),
    scalaVersion := "2.13.1",
    version := "0.0.0",
))
// format: on

val catsVersion           = "2.1.0"
val http4sVersion         = "0.21.0-RC5"
val circeVersion          = "0.13.0"
val circeConfigVersion    = "0.7.0"
val log4catsVersion       = "1.0.1"
val fs2Version            = "2.2.1"
val logbackVersion        = "1.2.3"
val typesafeConfigVersion = "1.4.0"
val scalaTestVersion      = "3.1.0"
val mockitoScalaVersion   = "1.11.2"

val dependencies = Seq(
  "org.http4s"        %% "http4s-blaze-client"     % http4sVersion,
  "org.http4s"        %% "http4s-dsl"              % http4sVersion,
  "org.http4s"        %% "http4s-circe"            % http4sVersion,
  "io.circe"          %% "circe-core"              % circeVersion,
  "io.circe"          %% "circe-literal"           % circeVersion,
  "io.circe"          %% "circe-generic"           % circeVersion,
  "io.circe"          %% "circe-config"            % circeConfigVersion,
  "io.circe"          %% "circe-generic-extras"    % circeVersion,
  "org.typelevel"     %% "cats-core"               % catsVersion,
  "org.typelevel"     %% "cats-effect"             % catsVersion,
  "org.typelevel"     %% "cats-effect-laws"        % catsVersion % "test",
  "co.fs2"            %% "fs2-core"                % fs2Version,
  "io.chrisdavenport" %% "log4cats-core"           % log4catsVersion,
  "io.chrisdavenport" %% "log4cats-slf4j"          % log4catsVersion,
  "io.chrisdavenport" %% "log4cats-testing"        % log4catsVersion % "test",
  "ch.qos.logback"    % "logback-classic"          % logbackVersion,
  "com.typesafe"      % "config"                   % typesafeConfigVersion,
  "org.scalatest"     %% "scalatest"               % scalaTestVersion % "test",
  "org.mockito"       %% "mockito-scala"           % mockitoScalaVersion % "test",
  "org.mockito"       %% "mockito-scala-scalatest" % mockitoScalaVersion % "test",
)

lazy val chaos = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "chaos",
    description := "A purely functional Discord build scraper.",
    startYear := Some(2020),
    homepage := Some(url("https://github.com/slice/chaos")),
    developers := List(
      Developer(
        "slice",
        "slice",
        "slice@slice.zone",
        url("https://slice.zone"),
      ),
    ),
    resolvers += Resolver.mavenCentral,
    libraryDependencies ++= dependencies,
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, homepage),
    buildInfoPackage := "zone.slice.chaos",
  )

scalacOptions in Test ++= Seq("-Yrangepos")
// A workaround for some type inference issues.
// See: https://github.com/mockito/mockito-scala/issues/29
scalacOptions in Test -= "-Wdead-code"
javaOptions in reStart += "-Dconfig.file=./application.conf"
