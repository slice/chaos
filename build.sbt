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

val dependencies = Seq(
  "org.http4s"        %% "http4s-blaze-client"  % http4sVersion,
  "org.http4s"        %% "http4s-dsl"           % http4sVersion,
  "org.http4s"        %% "http4s-circe"         % http4sVersion,
  "io.circe"          %% "circe-core"           % circeVersion,
  "io.circe"          %% "circe-literal"        % circeVersion,
  "io.circe"          %% "circe-generic"        % circeVersion,
  "io.circe"          %% "circe-config"         % circeConfigVersion,
  "io.circe"          %% "circe-generic-extras" % circeVersion,
  "org.typelevel"     %% "cats-core"            % catsVersion,
  "org.typelevel"     %% "cats-effect"          % catsVersion,
  "co.fs2"            %% "fs2-core"             % fs2Version,
  "io.chrisdavenport" %% "log4cats-core"        % log4catsVersion,
  "io.chrisdavenport" %% "log4cats-slf4j"       % log4catsVersion,
  "ch.qos.logback"    % "logback-classic"       % logbackVersion,
  "com.typesafe"      % "config"                % typesafeConfigVersion,
)

lazy val root = (project in file("."))
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

javaOptions in reStart += "-Dconfig.file=./application.conf"
