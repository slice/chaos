inThisBuild(
  Seq(
    organization := "zone.slice",
    organizationHomepage := Some(url("https://slice.zone")),
    homepage := Some(url("https://github.com/slice/chaos")),
    description := "a purely functional discord build scraper",
    scalaVersion := "2.13.1",
    version := "0.0.0",
    startYear := Some(2020),
    developers := List(
      Developer(
        "slice",
        "slice",
        "slice@slice.zone",
        url("https://slice.zone"),
      ),
    ),
  ),
)

val V = new {
  val cats              = "2.1.0"
  val http4s            = "0.21.0-RC5"
  val circe             = "0.13.0"
  val `circe-config`    = "0.7.0"
  val log4cats          = "1.0.1"
  val fs2               = "2.2.1"
  val logback           = "1.2.3"
  val `typesafe-config` = "1.4.0"
  val scalatest         = "3.1.0"
  val `mockito-scala`   = "1.11.2"
}

val dependencies = Seq(
  "org.http4s"        %% "http4s-blaze-client"     % V.http4s,
  "org.http4s"        %% "http4s-dsl"              % V.http4s,
  "org.http4s"        %% "http4s-circe"            % V.http4s,
  "io.circe"          %% "circe-core"              % V.circe,
  "io.circe"          %% "circe-literal"           % V.circe,
  "io.circe"          %% "circe-generic"           % V.circe,
  "io.circe"          %% "circe-config"            % V.`circe-config`,
  "io.circe"          %% "circe-generic-extras"    % V.circe,
  "org.typelevel"     %% "cats-core"               % V.cats,
  "org.typelevel"     %% "cats-effect"             % V.cats,
  "org.typelevel"     %% "cats-effect-laws"        % V.cats % Test,
  "co.fs2"            %% "fs2-core"                % V.fs2,
  "io.chrisdavenport" %% "log4cats-core"           % V.log4cats,
  "io.chrisdavenport" %% "log4cats-slf4j"          % V.log4cats,
  "io.chrisdavenport" %% "log4cats-testing"        % V.log4cats % Test,
  "ch.qos.logback"    % "logback-classic"          % V.logback,
  "com.typesafe"      % "config"                   % V.`typesafe-config`,
  "org.scalatest"     %% "scalatest"               % V.scalatest % Test,
  "org.mockito"       %% "mockito-scala"           % V.`mockito-scala` % Test,
  "org.mockito"       %% "mockito-scala-scalatest" % V.`mockito-scala` % Test,
)

lazy val chaos = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "chaos",
    resolvers += Resolver.mavenCentral,
    libraryDependencies ++= dependencies,
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, homepage),
    buildInfoPackage := "zone.slice.chaos",
    Test / scalacOptions ++= Seq("-Yrangepos"),
    // A workaround for some type inference issues.
    // See: https://github.com/mockito/mockito-scala/issues/29
    Test / scalacOptions -= "-Wdead-code",
    reStart / javaOptions += "-Dconfig.file=./application.conf",
  )
