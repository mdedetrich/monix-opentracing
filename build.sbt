val currentScalaVersion               = "2.12.10"
val openTracingVersion                = "0.33.0"
val monixVersion                      = "3.1.0"
val monixTestVersion                  = "3.2.0-4524fe2-SNAPSHOT"
val scalaTestVersion                  = "3.1.0"
val opentracingScalaConcurrentVersion = "0.0.6"
val scalacheckVersion                 = "1.14.0"

resolvers in ThisBuild += Resolver.sonatypeRepo("snapshots")

scalaVersion in ThisBuild := currentScalaVersion

crossScalaVersions in ThisBuild := Seq(currentScalaVersion, "2.13.1")

scalacOptions in Test in ThisBuild ++= Seq("-Yrangepos")

organization in ThisBuild := "org.mdedetrich"

homepage in ThisBuild := Some(url("https://github.com/mdedetrich/monix-opentracing"))
scmInfo in ThisBuild := Some(
  ScmInfo(url("https://github.com/mdedetrich/monix-opentracing"), "git@github.com:mdedetrich/monix-opentracing.git"))

developers in ThisBuild := List(
  Developer("mdedetrich", "Matthew de Detrich", "mdedetrich@gmail.com", url("https://github.com/mdedetrich"))
)

licenses in ThisBuild += ("MIT", url("https://opensource.org/licenses/MIT"))

publishMavenStyle in ThisBuild := true
publishTo in ThisBuild := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
publishArtifact in Test in ThisBuild := false
pomIncludeRepository in ThisBuild := (_ => false)

lazy val core = (project in file("core")).settings(
  libraryDependencies := Seq(
    "io.monix"               %% "monix-execution"              % monixVersion,
    "io.opentracing"         % "opentracing-api"               % openTracingVersion,
    "io.opentracing"         % "opentracing-util"              % openTracingVersion,
    "io.opentracing"         % "opentracing-mock"              % openTracingVersion % Test,
    "io.monix"               %% "monix"                        % monixTestVersion % Test,
    "io.opentracing.contrib" %% "opentracing-scala-concurrent" % opentracingScalaConcurrentVersion % Test,
    "org.scalatest"          %% "scalatest"                    % scalaTestVersion % Test,
    "org.scalacheck"         %% "scalacheck"                   % scalacheckVersion % Test
  ),
  name := "monix-opentracing",
  description := "Monix support for OpenTracing using Local"
)

lazy val task = (project in file("task"))
  .settings(
    libraryDependencies := Seq(
      "io.monix"               %% "monix"                        % monixVersion,
      "io.monix"               %% "monix"                        % monixTestVersion % Test,
      "io.opentracing"         % "opentracing-mock"              % openTracingVersion % Test,
      "io.opentracing.contrib" %% "opentracing-scala-concurrent" % opentracingScalaConcurrentVersion % Test,
      "org.scalatest"          %% "scalatest"                    % scalaTestVersion % Test,
      "org.scalacheck"         %% "scalacheck"                   % scalacheckVersion % Test
    ),
    name := "monix-opentracing-task",
    description := "Additional Monix Task interopt for OpenTracing"
  )
  .dependsOn(core)

val flagsFor11 = Seq(
  "-Xlint:_",
  "-Yconst-opt",
  "-Ywarn-infer-any",
  "-Yclosure-elim",
  "-Ydead-code",
  "-Xsource:2.12" // required to build case class construction
)

val flagsFor12 = Seq(
  "-Xlint:_",
  "-Ywarn-infer-any",
  "-opt-inline-from:<sources>"
)

val flagsFor13 = Seq(
  "-Xlint:_",
  "-opt-inline-from:<sources>"
)

scalacOptions in ThisBuild ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, n)) if n >= 13 =>
      flagsFor13
    case Some((2, n)) if n == 12 =>
      flagsFor12
    case Some((2, n)) if n == 11 =>
      flagsFor11
  }
}

// Since our tests are using a global ScopeManager context and the tests clear the context inbetween runs,
// we need to run them synchronously
parallelExecution in Test in ThisBuild := false
