import scalariform.formatter.preferences._
import com.scalapenos.sbt.prompt._
import SbtPrompt.autoImport._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
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
  scalaVersion := "2.12.7",
  crossScalaVersions := Seq("2.11.12", "2.12.7")
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
  useGpg := true,
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
    ReleaseStep(action = Command.process("sonatypeReleaseAll", _), enableCrossBuild = true)
    // pushChanges
  )
)

lazy val commonSettings = Seq(
  incOptions := incOptions.value.withLogRecompileOnMacro(false),
  scalacOptions ++= commonScalacOptions,
  libraryDependencies ++= Seq(
    compilerPlugin(D.macroParadise),
    compilerPlugin(D.kindProjector)
  ),
  fork in test := true,
  parallelExecution in Test := false,
  scalacOptions in (Compile, doc) := (scalacOptions in (Compile, doc)).value.filter(_ != "-Xfatal-warnings"),
  // workaround for https://github.com/scalastyle/scalastyle-sbt-plugin/issues/47
  scalastyleSources in Compile ++= (unmanagedSourceDirectories in Compile).value
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
  //  "-Yinline-warnings",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture",
  "-Xlog-reflective-calls",
  "-Ywarn-inaccessible",
  "-Ypatmat-exhaust-depth", "20",
  //  "-Ybackend:GenBCode",
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

lazy val protobufSettings = akka.Protobuf.settings

lazy val scoverageSettings = Seq(
  coverageMinimum := 60,
  coverageFailOnMinimum := false,
  coverageExcludedPackages := ".*generated.*;.*protobuf.*",
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
  scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value
)

lazy val wartRemoverSettings = Seq(
  wartremoverErrors ++= Warts.unsafe
)

lazy val micrositesSettings = Seq(
  micrositeName := "akka-d3",
  micrositeDescription := "Library for Domain Driven Design, embracing Event Sourcing and CQRS, on top of Akka",
  micrositeBaseUrl := "akka-d3",
  micrositeDocumentationUrl := "/akka-d3/docs/",
  micrositeGithubOwner := "pjan",
  micrositeGithubRepo := "akka-d3",
  micrositeAuthor := "pjan",
  micrositeHighlightTheme := "solarized-dark",
  includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.md"
)

lazy val buildInfoSettings = Seq(
  buildInfoKeys := Seq[BuildInfoKey](
    name,
    version,
    scalaVersion,
    sbtVersion,
    "lastTag" -> git.gitDescribedVersion.value.get.takeWhile(_ != '-')
  ),
  buildInfoPackage := "akka.contrib.d3"
)

lazy val d3Settings = buildSettings ++ commonSettings ++ publishSettings ++ formatSettings ++ promptSettings ++ scoverageSettings

///////////////////////////////////////////////////////////////////////////////////////////////////
// Dependencies
///////////////////////////////////////////////////////////////////////////////////////////////////

lazy val D = new {

  val Versions = new {
    val akka                     = "2.5.17"
    val akkaPersistenceCassandra = "0.98"
    val akkaPersistenceInMemory  = "2.5.1.1"

    // Test
    val scalaTest                = "3.0.5"

    // Compiler
    val kindProjector            = "0.9.8"
    val macroParadise            = "2.1.1"
  }

  val akkaActor                = "com.typesafe.akka"              %%  "akka-actor"                           % Versions.akka
  val akkaClusterSharding      = "com.typesafe.akka"              %%  "akka-cluster-sharding"                % Versions.akka
  val akkaPersistence          = "com.typesafe.akka"              %%  "akka-persistence"                     % Versions.akka
  val akkaPersistenceCassandra = "com.typesafe.akka"              %%  "akka-persistence-cassandra"           % Versions.akkaPersistenceCassandra
  val akkaPersistenceInMemory  = "com.github.dnvriend"            %%  "akka-persistence-inmemory"            % Versions.akkaPersistenceInMemory
  val akkaPersistenceQuery     = "com.typesafe.akka"              %%  "akka-persistence-query"               % Versions.akka

  // Test
  val akkaTest                 = "com.typesafe.akka"              %%  "akka-testkit"                         % Versions.akka
  val scalaTest                = "org.scalatest"                  %%  "scalatest"                            % Versions.scalaTest

  // Compiler
  val kindProjector            = "org.spire-math"                 %%  "kind-projector"                       % Versions.kindProjector // cross CrossVersion.full
  val macroParadise            = "org.scalamacros"                %%  "paradise"                             % Versions.macroParadise  cross CrossVersion.full

}

///////////////////////////////////////////////////////////////////////////////////////////////////
// Projects
///////////////////////////////////////////////////////////////////////////////////////////////////

lazy val root = Project(
    id = "akka-d3",
    base = file(".")
  )
  .settings(moduleName := "root")
  .settings(d3Settings)
  .settings(noPublishSettings)
  .aggregate(d3, queryCassandra, queryInmemory, readsideCassandra)
  .dependsOn(d3, queryCassandra, queryInmemory, readsideCassandra)

lazy val d3 = project.in(file(".d3"))
  .settings(moduleName := "akka-d3")
  .settings(d3Settings)
  .settings(commonJvmSettings)
  .aggregate(core, cluster)
  .dependsOn(core, cluster)

lazy val docs = Project(
    id = "docs",
    base = file("docs")
  )
  .settings(moduleName := "docs")
  .settings(d3Settings)
  .settings(micrositesSettings)
  .settings(buildInfoSettings)
  .dependsOn(root)
  .enablePlugins(MicrositesPlugin)
  .enablePlugins(BuildInfoPlugin)

lazy val core = Project(
    id = "core",
    base = file("akka-d3-core")
  )
  .settings(moduleName := "akka-d3-core")
  .settings(
  	libraryDependencies ++= Seq(
  	  D.akkaActor,
  	  D.akkaPersistence,
      D.akkaPersistenceQuery,
  	  D.akkaTest % "test",
  	  D.scalaTest % "test",
      D.akkaPersistenceInMemory % "test"
  	)
  )
  .settings(d3Settings)
  .settings(commonJvmSettings)
  .settings(protobufSettings)

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
      D.akkaPersistenceInMemory % "test"
    )
  )
  .dependsOn(core)
  .settings(d3Settings)
  .settings(commonJvmSettings)

lazy val queryInmemory = Project(
    id = "query-inmemory",
    base = file("akka-d3-query-inmemory")
  )
  .settings(moduleName := "akka-d3-query-inmemory")
  .settings(
    libraryDependencies ++= Seq(
      D.akkaPersistenceInMemory
    )
  )
  .dependsOn(core)
  .settings(d3Settings)
  .settings(commonJvmSettings)

lazy val queryCassandra = Project(
    id = "query-cassandra",
    base = file("akka-d3-query-cassandra")
  )
  .settings(moduleName := "akka-d3-query-cassandra")
  .settings(
    libraryDependencies ++= Seq(
      D.akkaPersistenceCassandra
    )
  )
  .dependsOn(core)
  .settings(d3Settings)
  .settings(commonJvmSettings)

lazy val readsideCassandra = Project(
    id = "readside-cassandra",
    base = file("akka-d3-readside-cassandra")
  )
  .settings(moduleName := "akka-d3-readside-cassandra")
  .settings(
    libraryDependencies ++= Seq(
      D.akkaPersistenceCassandra,
      // fix for SI-8978
      "com.google.code.findbugs" % "jsr305" % "3.0.2"
    )
  )
  .dependsOn(core, queryCassandra)
  .settings(d3Settings)
  .settings(commonJvmSettings)

///////////////////////////////////////////////////////////////////////////////////////////////////
// Commands
///////////////////////////////////////////////////////////////////////////////////////////////////

addCommandAlias("gitSnapshots", ";set version in ThisBuild := git.gitDescribedVersion.value.get + \"-SNAPSHOT\"")

addCommandAlias("build", ";clean;scalariformFormat;scalastyle;protobufGenerate;test")

addCommandAlias("validate", ";clean;scalastyle;protobufGenerate;test;docs/makeMicrosite")
