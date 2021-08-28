inThisBuild(
  Seq(
    organization := "zone.slice",
    organizationHomepage := Some(url("https://slice.zone")),
    homepage := Some(url("https://github.com/slice/chaos")),
    description := "a purely functional discord build scraper",
    scalaVersion := "2.13.6",
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
  val cats              = "2.6.1"
  val `cats-effect`     = "3.2.4"
  val http4s            = "0.23.1"
  val circe             = "0.14.1"
  val `circe-config`    = "0.8.0"
  val log4cats          = "2.1.1"
  val fs2               = "3.1.1"
  val logback           = "1.2.3"
  val `typesafe-config` = "1.4.0"
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
  "org.typelevel"     %% "cats-effect-kernel"      % V.`cats-effect`,
  "org.typelevel"     %% "cats-effect"             % V.`cats-effect`,
  "org.typelevel"     %% "cats-effect-std"         % V.`cats-effect`,
  "co.fs2"            %% "fs2-core"                % V.fs2,
  "org.typelevel"     %% "log4cats-core"           % V.log4cats,
  "org.typelevel"     %% "log4cats-slf4j"          % V.log4cats,
  "ch.qos.logback"     % "logback-classic"         % V.logback,
  "com.typesafe"       % "config"                  % V.`typesafe-config`,
  "org.systemfw"      %% "upperbound"              % "0.3.1-SNAPSHOT",
)

lazy val chaos = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  // .dependsOn(ProjectRef(uri("https://github.com/SystemFW/upperbound.git"), "root"))
  // .dependsOn(RootProject(uri("git://github.com/SystemFW/upperbound.git")))
  .settings(
    name := "chaos",
    resolvers += Resolver.mavenCentral,
    libraryDependencies ++= dependencies,
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, homepage),
    buildInfoPackage := "zone.slice.chaos",
    Test / scalacOptions += "-Yrangepos",
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    // Fork when running because we spawn threads.
    run / fork := true,
    run / javaOptions += "-Dconfig.file=./application.conf",
  )
