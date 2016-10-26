import scalariform.formatter.preferences._
import com.scalapenos.sbt.prompt._
import SbtPrompt.autoImport._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import spray.revolver.RevolverPlugin.Revolver
import ReleaseTransformations._

import scala.xml.transform.{RewriteRule, RuleTransformer}
import scalariform.formatter.preferences.AlignSingleLineCaseStatements.MaxArrowIndent

///////////////////////////////////////////////////////////////////////////////////////////////////
// Settings
///////////////////////////////////////////////////////////////////////////////////////////////////

promptTheme := PromptTheme(List(
  text("[SBT] ", fg(136)),
  currentProject(fg(64)).padRight(": ")
))

lazy val tagName = Def.setting{
  s"v${if (releaseUseGlobalVersion.value) (version in ThisBuild).value else version.value}"
}

lazy val buildSettings = Seq(
  organization := "io.pjan",
  scalaVersion := "2.11.8",
  crossScalaVersions := Seq("2.11.8")
)

lazy val noPublishSettings = Seq(
  publish         := { },
  publishLocal    := { },
  publishArtifact := false
)

lazy val noTests = Seq(
  test in test := { },
  coverageEnabled := false
)

lazy val publishSettings = Seq(
  homepage := Some(url("https://github.com/pjan/akka-d3")),
  licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT")),
  scmInfo := Some(ScmInfo(url("https://github.com/pjan/akka-d3"), "scm:git:git@github.com:pjan/akka-d3.git")),
  autoAPIMappings := true,
  pomExtra :=
    <developers>
      <developer>
        <id>pjan</id>
        <name>pjan vandaele</name>
        <url>https://github.com/pjan/</url>
      </developer>
    </developers>
) ++ credentialSettings ++ sharedPublishSettings ++ sharedReleaseProcess

lazy val credentialSettings = Seq(
  // For Travis CI - see http://www.cakesolutions.net/teamblogs/publishing-artefacts-to-oss-sonatype-nexus-using-sbt-and-travis-ci
  credentials ++= (for {
    username <- Option(System.getenv().get("SONATYPE_USERNAME"))
    password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
  } yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)).toSeq
)

lazy val sharedPublishSettings = Seq(
  releaseCrossBuild := true,
  releaseTagName := tagName.value,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := Function.const(false),
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("Snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("Releases" at nexus + "service/local/staging/deploy/maven2")
  }
)

lazy val sharedReleaseProcess = Seq(
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    releaseStepCommand("build"),
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    setNextVersion,
    commitNextVersion,
    ReleaseStep(action = Command.process("sonatypeReleaseAll", _), enableCrossBuild = true),
    pushChanges)
)

lazy val commonSettings = Seq(
  incOptions := incOptions.value.withLogRecompileOnMacro(false),
  scalacOptions ++= commonScalacOptions,
  libraryDependencies ++= Seq(
    D.simulacrum,
    D.machinist,
    compilerPlugin(D.macroParadise),
    compilerPlugin(D.kindProjector)
  ),
  fork in test := true,
  parallelExecution in Test := false,
  scalacOptions in (Compile, doc) := (scalacOptions in (Compile, doc)).value.filter(_ != "-Xfatal-warnings"),
  // workaround for https://github.com/scalastyle/scalastyle-sbt-plugin/issues/47
  (scalastyleSources in Compile) <++= unmanagedSourceDirectories in Compile
) ++ warnUnusedImport

lazy val formatSettings = SbtScalariform.scalariformSettings ++ Seq(
    ScalariformKeys.preferences := ScalariformKeys.preferences.value
      .setPreference(RewriteArrowSymbols, true)
      .setPreference(DoubleIndentClassDeclaration, true)
      .setPreference(PreserveSpaceBeforeArguments, true)
      .setPreference(AlignParameters, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(MaxArrowIndent, 75)
  )

lazy val commonScalacOptions = Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:experimental.macros",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint",
  "-Yinline-warnings",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture",
  "-Xlog-reflective-calls",
  "-Ywarn-inaccessible",
  "-Ypatmat-exhaust-depth", "20",
  "-Ybackend:GenBCode",
  "-Ydelambdafy:method"
)

lazy val commonJvmSettings = Seq(
  testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oDF")
)

lazy val promptSettings = Seq(
  promptTheme := PromptTheme(List(
    text("[SBT] ", fg(136)),
    currentProject(fg(64)).padRight(": ")
  ))
)

lazy val scoverageSettings = Seq(
  coverageMinimum := 60,
  coverageFailOnMinimum := false,
  coverageExcludedPackages := ".*generated.*;.*protobuf.*;.*examples.*",
  // don't include scoverage as a dependency in the pom
  // see issue #980
  // this code was copied from https://github.com/mongodb/mongo-spark
  pomPostProcess := { (node: xml.Node) =>
    new RuleTransformer(
      new RewriteRule {
        override def transform(node: xml.Node): Seq[xml.Node] = node match {
          case e: xml.Elem if e.label == "dependency" && e.child.exists(child => child.label == "groupId" && child.text == "org.scoverage") =>
            Nil
          case _ =>
            Seq(node)
        }
      }
    ).transform(node).head
  }
)

lazy val warnUnusedImport = Seq(
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 10)) =>
        Seq()
      case Some((2, n)) if n >= 11 =>
        Seq("-Ywarn-unused-import")
    }
  },
  scalacOptions in (Compile, console) ~= {_.filterNot("-Ywarn-unused-import" == _)},
  scalacOptions in (Test, console) <<= (scalacOptions in (Compile, console))
)

lazy val revolverSettings =
  Revolver.settings

lazy val wartRemoverSettings = Seq(
  wartremoverErrors ++= Warts.unsafe
)

lazy val d3Settings = buildSettings ++ commonSettings ++ publishSettings ++ formatSettings ++ promptSettings ++ revolverSettings ++ scoverageSettings

///////////////////////////////////////////////////////////////////////////////////////////////////
// Dependencies
///////////////////////////////////////////////////////////////////////////////////////////////////

lazy val D = new {

  val Versions = new {
    val akka                     = "2.4.11"
    val akkaPersistenceInMemory  = "1.3.10"
    val machinist                = "0.5.0"
    val simulacrum               = "0.9.0"
    val typesafeConfig           = "1.2.1"

    // Test
    val scalaMock                = "3.2.2"
    val scalaCheck               = "1.13.2"
    val scalaTest                = "3.0.0"

    // Compiler
    val kindProjector            = "0.9.0"
    val macroParadise            = "2.1.0"
  }

  val akkaActor                = "com.typesafe.akka"              %%  "akka-actor"                           % Versions.akka
  val akkaCluster              = "com.typesafe.akka"              %%  "akka-cluster"                         % Versions.akka
  val akkaClusterSharding      = "com.typesafe.akka"              %%  "akka-cluster-sharding"                % Versions.akka
  val akkaHttp                 = "com.typesafe.akka"              %%  "akka-http-experimental"               % Versions.akka
  val akkaHttpCore             = "com.typesafe.akka"              %%  "akka-http-core-experimental"          % Versions.akka
  val akkaHttpXml              = "com.typesafe.akka"              %%  "akka-http-xml-experimental"           % Versions.akka
  val akkaPersistence          = "com.typesafe.akka"              %%  "akka-persistence"                     % Versions.akka
  val akkaSlf4j                = "com.typesafe.akka"              %%  "akka-slf4j"                           % Versions.akka
  val akkaStream               = "com.typesafe.akka"              %%  "akka-stream"                          % Versions.akka
  val machinist                = "org.typelevel"                  %%  "machinist"                            % Versions.machinist
  val simulacrum               = "com.github.mpilquist"           %%  "simulacrum"                           % Versions.simulacrum
  val typesafeConfig           = "com.typesafe"                   %   "config"                               % Versions.typesafeConfig

  // Test
  val akkaHttpTest             = "com.typesafe.akka"              %%  "akka-http-testkit"                    % Versions.akka
  val akkaTest                 = "com.typesafe.akka"              %%  "akka-testkit"                         % Versions.akka
  val akkaPersistenceInMemory  = "com.github.dnvriend"            %%  "akka-persistence-inmemory"            % Versions.akkaPersistenceInMemory
  val akkaStreamTest           = "com.typesafe.akka"              %%  "akka-stream-testkit"                  % Versions.akka
  val scalaCheck               = "org.scalacheck"                 %%  "scalacheck"                           % Versions.scalaCheck
  val scalaMock                = "org.scalamock"                  %%  "scalamock-scalatest-support"          % Versions.scalaMock
  val scalaTest                = "org.scalatest"                  %%  "scalatest"                            % Versions.scalaTest

  // Compiler
  val kindProjector            = "org.spire-math"                 %%  "kind-projector"                       % Versions.kindProjector // cross CrossVersion.full
  val macroParadise            = "org.scalamacros"                %%  "paradise"                             % Versions.macroParadise  cross CrossVersion.full

}

///////////////////////////////////////////////////////////////////////////////////////////////////
// Projects
///////////////////////////////////////////////////////////////////////////////////////////////////

lazy val d3 = project.in(file("."))
  .settings(moduleName := "root")
  .settings(d3Settings)
  .settings(noPublishSettings)
  .aggregate(core, cluster)
  .dependsOn(core, cluster)

lazy val core = Project(
    id = "core",
    base = file("akka-d3-core")
  )
  .settings(moduleName := "akka-d3-core")
  .settings(
  	libraryDependencies ++= Seq(
  	  D.akkaActor,
  	  D.akkaPersistence,
  	  D.akkaTest % "test",
  	  D.scalaTest % "test",
      D.akkaPersistenceInMemory % "test",
      compilerPlugin(D.kindProjector)
  	)
  )
  .settings(d3Settings)
  .settings(commonJvmSettings)

lazy val cluster = Project(
    id = "cluster",
    base = file("akka-d3-cluster")
  )
  .settings(moduleName := "akka-d3-cluster")
  .settings(
    libraryDependencies ++= Seq(
      D.akkaClusterSharding,
      D.akkaTest % "test",
      D.scalaTest % "test",
      D.akkaPersistenceInMemory % "test",
      compilerPlugin(D.kindProjector)
    )
  )
  .dependsOn(core)
  .settings(d3Settings)
  .settings(commonJvmSettings)

lazy val examples = Project(
    id = "examples",
    base = file("examples")
  )
  .settings(moduleName := "examples")
  .settings(libraryDependencies ++= Seq(
      D.akkaPersistenceInMemory
    )
  )
  .dependsOn(core, cluster)
  .settings(d3Settings)
  .settings(commonJvmSettings)
  .settings(noPublishSettings)
  .settings(noTests)

///////////////////////////////////////////////////////////////////////////////////////////////////
// Commands
///////////////////////////////////////////////////////////////////////////////////////////////////

addCommandAlias("gitSnapshots", ";set version in ThisBuild := git.gitDescribedVersion.value.get + \"-SNAPSHOT\"")

addCommandAlias("build", ";clean;scalariformFormat;scalastyle;test")

addCommandAlias("validate", ";clean;scalastyle;test")
