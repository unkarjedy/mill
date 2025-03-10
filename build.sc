// plugins and dependencies
import $file.ci.shared
import $file.ci.upload
import $ivy.`org.scalaj::scalaj-http:2.4.2`
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version_mill0.10:0.3.0`
import $ivy.`com.github.lolgab::mill-mima_mill0.10:0.0.13`
import $ivy.`net.sourceforge.htmlcleaner:htmlcleaner:2.25`

// imports
import com.github.lolgab.mill.mima
import com.github.lolgab.mill.mima.{
  CheckDirection,
  DirectMissingMethodProblem,
  IncompatibleMethTypeProblem,
  IncompatibleSignatureProblem,
  ProblemFilter,
  ReversedMissingMethodProblem
}
import coursier.maven.MavenRepository
import de.tobiasroeser.mill.vcs.version.VcsVersion
import mill._
import mill.define.{Command, Source, Sources, Target, Task}
import mill.eval.Evaluator
import mill.main.MainModule
import mill.scalalib._
import mill.scalalib.publish._
import mill.modules.Jvm
import mill.define.SelectMode

object Settings {
  val pomOrg = "com.lihaoyi"
  val githubOrg = "com-lihaoyi"
  val githubRepo = "mill"
  val projectUrl = s"https://github.com/${githubOrg}/${githubRepo}"
  val changelogUrl = s"${projectUrl}#changelog"
  val docUrl = "https://com-lihaoyi.github.io/mill"
  // the exact branches containing a doc root
  val docBranches = Seq()
  // the exact tags containing a doc root
  val docTags: Seq[String] = Seq(
    "0.9.6",
    "0.9.7",
    "0.9.8",
    "0.9.9",
    "0.9.10",
    "0.9.11",
    "0.9.12",
    "0.10.0",
    "0.10.1",
    "0.10.2",
    "0.10.3",
    "0.10.4",
    "0.10.5",
    "0.10.6",
    "0.10.7",
    "0.10.8",
    "0.10.9",
    "0.10.10",
    "0.11.0-M1",
    "0.11.0-M2"
  )
  val mimaBaseVersions: Seq[String] = Seq("0.11.0-M2")
}

object Deps {

  // The Scala version to use
  val scalaVersion = "2.13.10"
  // Scoverage 1.x will not get releases for newer Scala versions
  val scalaVersionForScoverageWorker1 = "2.13.8"
  // The Scala 2.12.x version to use for some workers
  val workerScalaVersion212 = "2.12.17"

  val testScala213Version = "2.13.8"
  val testScala212Version = "2.12.6"
  val testScala211Version = "2.11.12"
  val testScala210Version = "2.10.6"
  val testScala30Version = "3.0.2"
  val testScala31Version = "3.1.3"
  val testScala32Version = "3.2.0"

  object Scalajs_1 {
    val scalaJsVersion = "1.12.0"
    val scalajsEnvJsdomNodejs = ivy"org.scala-js::scalajs-env-jsdom-nodejs:1.1.0"
    val scalajsEnvExoegoJsdomNodejs = ivy"net.exoego::scalajs-env-jsdom-nodejs:2.1.0"
    val scalajsEnvNodejs = ivy"org.scala-js::scalajs-env-nodejs:1.4.0"
    val scalajsEnvPhantomjs = ivy"org.scala-js::scalajs-env-phantomjs:1.0.0"
    val scalajsSbtTestAdapter = ivy"org.scala-js::scalajs-sbt-test-adapter:${scalaJsVersion}"
    val scalajsLinker = ivy"org.scala-js::scalajs-linker:${scalaJsVersion}"
  }

  object Scalanative_0_4 {
    val scalanativeVersion = "0.4.9"
    val scalanativeTools = ivy"org.scala-native::tools:${scalanativeVersion}"
    val scalanativeUtil = ivy"org.scala-native::util:${scalanativeVersion}"
    val scalanativeNir = ivy"org.scala-native::nir:${scalanativeVersion}"
    val scalanativeTestRunner = ivy"org.scala-native::test-runner:${scalanativeVersion}"
  }

  trait Play {
    def playVersion: String
    def playBinVersion: String = playVersion.split("[.]").take(2).mkString(".")
    def routesCompiler = ivy"com.typesafe.play::routes-compiler::$playVersion"
    def scalaVersion: String = Deps.scalaVersion
  }
  object Play_2_6 extends Play {
    val playVersion = "2.6.25"
    override def scalaVersion: String = Deps.workerScalaVersion212
  }
  object Play_2_7 extends Play {
    val playVersion = "2.7.9"
  }
  object Play_2_8 extends Play {
    val playVersion = "2.8.18"
  }
  val play = Seq(Play_2_8, Play_2_7, Play_2_6).map(p => (p.playBinVersion, p)).toMap

  val acyclic = ivy"com.lihaoyi:::acyclic:0.3.6"
  val ammoniteVersion = "2.5.6"
  val ammonite = ivy"com.lihaoyi:::ammonite:${ammoniteVersion}"
  val ammoniteTerminal = ivy"com.lihaoyi::ammonite-terminal:${ammoniteVersion}"
  val ammoniteReducedDeps = ammonite.exclude(
    // Exclude trees here to force the version of the dependencies we have defined ourselves.
    // We use this here instead of a `forceVersion()` on scalametaTrees since it's not
    // respected in the POM causing issues for Coursier Mill users.
    "org.scalameta" -> "trees_2.13",
    // only used when ammonite is run with --bsp, which we don't support
    "ch.epfl.scala" -> "bsp4j"
  )
  val asciidoctorj = ivy"org.asciidoctor:asciidoctorj:2.4.3"
  val bloopConfig = ivy"ch.epfl.scala::bloop-config:1.5.5"
  // avoid version 2.1.0-RC2 for issue https://github.com/coursier/coursier/issues/2603
  val coursier = ivy"io.get-coursier::coursier:2.1.0-RC4"

  val flywayCore = ivy"org.flywaydb:flyway-core:8.5.13"
  val graphvizJava = ivy"guru.nidi:graphviz-java-all-j2v8:0.18.1"
  val junixsocket = ivy"com.kohlschutter.junixsocket:junixsocket-core:2.6.1"

  val jgraphtCore = ivy"org.jgrapht:jgrapht-core:1.4.0" // 1.5.0+ dont support JDK8

  val jna = ivy"net.java.dev.jna:jna:5.13.0"
  val jnaPlatform = ivy"net.java.dev.jna:jna-platform:5.13.0"

  val junitInterface = ivy"com.github.sbt:junit-interface:0.13.3"
  val lambdaTest = ivy"de.tototec:de.tobiasroeser.lambdatest:0.7.1"
  val log4j2Core = ivy"org.apache.logging.log4j:log4j-core:2.19.0"
  val osLib = ivy"com.lihaoyi::os-lib:0.9.0"
  val millModuledefsVersion = "0.10.9"
  val millModuledefs = ivy"com.lihaoyi::mill-moduledefs:${millModuledefsVersion}"
  val millModuledefsPlugin =
    ivy"com.lihaoyi:::scalac-mill-moduledefs-plugin:${millModuledefsVersion}"
  val testng = ivy"org.testng:testng:7.5"
  val sbtTestInterface = ivy"org.scala-sbt:test-interface:1.0"
  val scalaCheck = ivy"org.scalacheck::scalacheck:1.17.0"
  def scalaCompiler(scalaVersion: String) = ivy"org.scala-lang:scala-compiler:${scalaVersion}"
  val scalafmtDynamic = ivy"org.scalameta::scalafmt-dynamic:3.6.1"
  val scalametaTrees = ivy"org.scalameta::trees:4.7.2"
  def scalaReflect(scalaVersion: String) = ivy"org.scala-lang:scala-reflect:${scalaVersion}"
  val scalacScoveragePlugin = ivy"org.scoverage:::scalac-scoverage-plugin:1.4.11"
  val scoverage2Version = "2.0.7"
  val scalacScoverage2Plugin = ivy"org.scoverage:::scalac-scoverage-plugin:${scoverage2Version}"
  val scalacScoverage2Reporter = ivy"org.scoverage::scalac-scoverage-reporter:${scoverage2Version}"
  val scalacScoverage2Domain = ivy"org.scoverage::scalac-scoverage-domain:${scoverage2Version}"
  val scalacScoverage2Serializer =
    ivy"org.scoverage::scalac-scoverage-serializer:${scoverage2Version}"
  // keep in sync with doc/antora/antory.yml
  val semanticDB = ivy"org.scalameta:::semanticdb-scalac:4.7.2"
  // when bumping this, also update SemanticDbJavaModule.scala to use -build-tool:mill
  // see https://github.com/sourcegraph/scip-java/pull/527
  val semanticDbJava = ivy"com.sourcegraph:semanticdb-java:0.8.9"
  val sourcecode = ivy"com.lihaoyi::sourcecode:0.3.0"
  val upickle = ivy"com.lihaoyi::upickle:3.0.0-M1"
  val utest = ivy"com.lihaoyi::utest:0.8.1"
  val windowsAnsi = ivy"io.github.alexarchambault.windows-ansi:windows-ansi:0.0.4"
  val zinc = ivy"org.scala-sbt::zinc:1.8.0"
  // keep in sync with doc/antora/antory.yml
  val bsp4j = ivy"ch.epfl.scala:bsp4j:2.1.0-M3"
  val fansi = ivy"com.lihaoyi::fansi:0.4.0"
  val jarjarabrams = ivy"com.eed3si9n.jarjarabrams::jarjar-abrams-core:1.8.1"
  val requests = ivy"com.lihaoyi::requests:0.8.0"
}

def millVersion: T[String] = T { VcsVersion.vcsState().format() }
def millLastTag: T[String] = T {
  VcsVersion.vcsState().lastTag.getOrElse(
    sys.error("No (last) git tag found. Your git history seems incomplete!")
  )
}
def millBinPlatform: T[String] = T {
  val tag = millLastTag()
  if (tag.contains("-M")) tag
  else {
    val pos = if (tag.startsWith("0.")) 2 else 1
    tag.split("[.]", pos + 1).take(pos).mkString(".")
  }
}
def baseDir = build.millSourcePath

trait MillPublishModule extends PublishModule {
  override def artifactName = "mill-" + super.artifactName()
  def publishVersion = millVersion()
  override def publishProperties: Target[Map[String, String]] = super.publishProperties() ++ Map(
    "info.releaseNotesURL" -> Settings.changelogUrl
  )
  def pomSettings = PomSettings(
    description = artifactName(),
    organization = Settings.pomOrg,
    url = Settings.projectUrl,
    licenses = Seq(License.MIT),
    versionControl = VersionControl.github(Settings.githubOrg, Settings.githubRepo),
    developers = Seq(
      Developer("lihaoyi", "Li Haoyi", "https://github.com/lihaoyi"),
      Developer("lefou", "Tobias Roeser", "https://github.com/lefou")
    )
  )
  override def javacOptions = Seq("-source", "1.8", "-target", "1.8", "-encoding", "UTF-8")
}

trait MillCoursierModule extends CoursierModule {
  override def repositoriesTask = T.task {
    super.repositoriesTask() ++ Seq(
      MavenRepository(
        "https://oss.sonatype.org/content/repositories/releases"
      )
    )
  }
  override def mapDependencies: Task[coursier.Dependency => coursier.Dependency] = T.task {
    super.mapDependencies().andThen { dep =>
      forcedVersions.find(t =>
        t._1 == dep.module.organization.value && t._2 == dep.module.name.value
      ).map { forced =>
        val newDep = dep.withVersion(forced._3)
        T.log.debug(s"Forcing version of ${dep.module} from ${dep.version} to ${newDep.version}")
        newDep
      }.getOrElse(dep)
    }
  }
  val forcedVersions: Seq[(String, String, String)] = Seq(
    ("org.apache.ant", "ant", "1.10.12"),
    ("commons-io", "commons-io", "2.11.0"),
    ("com.google.code.gson", "gson", "2.10.1"),
    ("com.google.protobuf", "protobuf-java", "3.21.8"),
    ("com.google.guava", "guava", "31.1-jre")
  )
}

trait MillMimaConfig extends mima.Mima {
  override def mimaPreviousVersions: T[Seq[String]] = Settings.mimaBaseVersions
  override def mimaPreviousArtifacts =
    if (Settings.mimaBaseVersions.isEmpty) T { Agg[Dep]() }
    else super.mimaPreviousArtifacts
  override def mimaExcludeAnnotations: T[Seq[String]] = Seq(
    "mill.api.internal",
    "mill.api.experimental"
  )
  override def mimaCheckDirection: Target[CheckDirection] = T { CheckDirection.Backward }
  override def mimaBinaryIssueFilters: Target[Seq[ProblemFilter]] = T {
    issueFilterByModule.getOrElse(this, Seq())
  }
  lazy val issueFilterByModule: Map[MillMimaConfig, Seq[ProblemFilter]] = Map()
}

/** A Module compiled with applied Mill-specific compiler plugins: mill-moduledefs. */
trait WithMillCompiler extends ScalaModule {
  override def ivyDeps: T[Agg[Dep]] = super.ivyDeps() ++ Agg(Deps.millModuledefs)
  override def scalacPluginIvyDeps: Target[Agg[Dep]] =
    super.scalacPluginIvyDeps() ++ Agg(Deps.millModuledefsPlugin)
}

trait AcyclicConfig extends ScalaModule {
  override def scalacPluginIvyDeps: Target[Agg[Dep]] = {
    super.scalacPluginIvyDeps() ++ Agg(Deps.acyclic)
  }
  override def scalacOptions: Target[Seq[String]] =
    super.scalacOptions() ++ Seq("-P:acyclic:force", "-P:acyclic:warn")
}

/**
 * Some custom scala settings and test convenience
 */
trait MillScalaModule extends ScalaModule with MillCoursierModule { outer =>
  def scalaVersion = Deps.scalaVersion
  override def scalacOptions = T {
    super.scalacOptions() ++ Seq("-deprecation")
  }
  override def ammoniteVersion = Deps.ammonite.dep.version

  // Test setup

  def testArgs = T { Seq.empty[String] }
  def testIvyDeps: T[Agg[Dep]] = Agg(Deps.utest)
  def testModuleDeps: Seq[JavaModule] =
    if (this == main) Seq(main)
    else Seq(this, main.test)

  trait MillScalaModuleTests extends ScalaModuleTests with MillCoursierModule
      with WithMillCompiler {
    override def forkArgs = T {
      Seq(
        s"-DMILL_SCALA_2_13_VERSION=${Deps.scalaVersion}",
        s"-DMILL_SCALA_2_12_VERSION=${Deps.workerScalaVersion212}",
        s"-DTEST_SCALA_2_13_VERSION=${Deps.testScala213Version}",
        s"-DTEST_SCALA_2_12_VERSION=${Deps.testScala212Version}",
        s"-DTEST_SCALA_2_11_VERSION=${Deps.testScala211Version}",
        s"-DTEST_SCALA_2_10_VERSION=${Deps.testScala210Version}",
        s"-DTEST_SCALA_3_0_VERSION=${Deps.testScala30Version}",
        s"-DTEST_SCALA_3_1_VERSION=${Deps.testScala31Version}",
        s"-DTEST_SCALA_3_2_VERSION=${Deps.testScala32Version}",
        s"-DTEST_SCALAJS_VERSION=${Deps.Scalajs_1.scalaJsVersion}",
        s"-DTEST_SCALANATIVE_VERSION=${Deps.Scalanative_0_4.scalanativeVersion}",
        s"-DTEST_UTEST_VERSION=${Deps.utest.dep.version}"
      ) ++ outer.testArgs()
    }
    override def moduleDeps = outer.testModuleDeps
    override def ivyDeps: T[Agg[Dep]] = T { super.ivyDeps() ++ outer.testIvyDeps() }
    override def testFramework = "mill.UTestFramework"
  }
  trait Tests extends MillScalaModuleTests
}

/** A MillScalaModule with default set up test module. */
trait MillAutoTestSetup extends MillScalaModule {
  // instead of `object test` which can't be overridden, we hand-made a val+class singleton
  /** Default tests module. */
  val test = new Tests(implicitly)
  class Tests(ctx0: mill.define.Ctx) extends mill.Module()(ctx0) with super.MillScalaModuleTests
}

/** Published module which does not contain strictly handled API. */
trait MillInternalModule extends MillScalaModule with MillPublishModule

/** Publishable module which contains strictly handled API. */
trait MillApiModule extends MillScalaModule with MillPublishModule with MillMimaConfig

/** Publishable module with tests. */
trait MillModule extends MillApiModule with MillAutoTestSetup with WithMillCompiler
    with AcyclicConfig

object main extends MillModule {

  override def moduleDeps = Seq(core, client)
  override def ivyDeps = Agg(
    Deps.windowsAnsi
  )
  override def compileIvyDeps = Agg(
    Deps.scalaReflect(scalaVersion())
  )
  override def testArgs = Seq(
    "-DMILL_VERSION=" + publishVersion()
  )

  object api extends MillApiModule {
    override def ivyDeps = Agg(
      Deps.osLib,
      Deps.upickle,
      Deps.sbtTestInterface
    )
  }
  object util extends MillApiModule {
    override def moduleDeps = Seq(api)
    def ivyDeps = Agg(
      Deps.ammoniteTerminal,
      Deps.fansi
    )
  }
  object core extends MillModule {
    override def moduleDeps = Seq(api, util)
    override def compileIvyDeps = Agg(
      Deps.scalaReflect(scalaVersion())
    )
    override def ivyDeps = Agg(
      Deps.millModuledefs,
      Deps.millModuledefsPlugin,
      Deps.ammoniteReducedDeps,
      Deps.scalametaTrees,
      Deps.coursier,
      // Necessary so we can share the JNA classes throughout the build process
      Deps.jna,
      Deps.jnaPlatform,
      Deps.jarjarabrams
    )
    override def generatedSources = T {
      val dest = T.ctx.dest
      writeBuildInfo(
        dir = dest,
        scalaVersion = scalaVersion(),
        millVersion = publishVersion(),
        millBinPlatform = millBinPlatform(),
        artifacts = T.traverse(dev.moduleDeps)(_.publishSelfDependency)()
      )
      Seq(PathRef(dest))
    }

    def writeBuildInfo(
        dir: os.Path,
        scalaVersion: String,
        millVersion: String,
        millBinPlatform: String,
        artifacts: Seq[Artifact]
    ) = {
      val code =
        s"""
           |package mill
           |
           |/** Generated by mill. */
           |object BuildInfo {
           |  /** Scala version used to compile mill core. */
           |  val scalaVersion = "$scalaVersion"
           |  /** Scala 2.12 version used by some workers. */
           |  val workerScalaVersion212 = "${Deps.workerScalaVersion212}"
           |  /** Mill version. */
           |  val millVersion = "$millVersion"
           |  /** Mill binary platform version. */
           |  val millBinPlatform = "$millBinPlatform"
           |  /** Dependency artifacts embedded in mill assembly by default. */
           |  val millEmbeddedDeps = ${artifacts.map(artifact =>
            s""""${artifact.group}:${artifact.id}:${artifact.version}""""
          )}
           |  /** Mill documentation url. */
           |  val millDocUrl = "${Settings.docUrl}"
           |}
      """.stripMargin.trim

      os.write(dir / "mill" / "BuildInfo.scala", code, createFolders = true)
    }
  }

  object client extends MillPublishModule {
    override def ivyDeps = Agg(
      Deps.junixsocket
    )
    def generatedBuildInfo: T[Seq[PathRef]] = T {
      val dest = T.dest
      val code =
        s"""package mill.main.client;
           |
           |/** Generated by mill. */
           |public class BuildInfo {
           |  /** Mill version. */
           |  public static String millVersion() { return "${millVersion()}"; }
           |}
           |""".stripMargin
      os.write(dest / "mill" / "main" / "client" / "BuildInfo.java", code, createFolders = true)
      Seq(PathRef(dest))
    }
    override def generatedSources: T[Seq[PathRef]] =
      super.generatedSources() ++ generatedBuildInfo()
    object test extends Tests with TestModule.Junit4 {
      override def ivyDeps = Agg(Deps.junitInterface, Deps.lambdaTest)
    }
  }

  object graphviz extends MillModule {
    override def moduleDeps = Seq(main, scalalib)

    override def ivyDeps = Agg(
      Deps.graphvizJava,
      Deps.jgraphtCore
    )
    override def testArgs = Seq(
      "-DMILL_GRAPHVIZ=" + runClasspath().map(_.path).mkString(",")
    )
  }

  object testkit extends MillInternalModule with MillAutoTestSetup {
    def moduleDeps = Seq(core, util)
  }

  def testModuleDeps = super.testModuleDeps ++ Seq(testkit)

}

object testrunner extends MillModule {
  override def moduleDeps = Seq(scalalib.api, main.util)
}
object scalalib extends MillModule {
  override def moduleDeps = Seq(main, scalalib.api, testrunner)

  override def ivyDeps = Agg(
    Deps.scalafmtDynamic
  )

  def genTask(m: ScalaModule) = T.task {
    Seq(m.jar(), m.sourceJar()) ++
      m.runClasspath()
  }

  override def generatedSources = T {
    val dest = T.ctx.dest
    val artifacts = T.traverse(dev.moduleDeps)(_.publishSelfDependency)()
    os.write(
      dest / "Versions.scala",
      s"""package mill.scalalib
         |
         |/**
         | * Dependency versions as they where defined at Mill compile time.
         | * Generated from mill in build.sc.
         | */
         |object Versions {
         |  /** Version of Ammonite. */
         |  val ammonite = "${Deps.ammonite.dep.version}"
         |  /** Version of Zinc. */
         |  val zinc = "${Deps.zinc.dep.version}"
         |  /** SemanticDB version. */
         |  val semanticDBVersion = "${Deps.semanticDB.dep.version}"
         |  /** Java SemanticDB plugin version. */
         |  val semanticDbJavaVersion = "${Deps.semanticDbJava.dep.version}"
         |}
         |
         |""".stripMargin
    )
    super.generatedSources() ++ Seq(PathRef(dest))
  }

  override def testIvyDeps = super.testIvyDeps() ++ Agg(Deps.scalaCheck)
  def testArgs = T {
    val genIdeaArgs =
//      genTask(main.moduledefs)() ++
      genTask(main.core)() ++
        genTask(main)() ++
        genTask(scalalib)() ++
        genTask(scalajslib)() ++
        genTask(scalanativelib)()

    worker.testArgs() ++
      main.graphviz.testArgs() ++
      Seq(
        "-Djna.nosys=true",
        "-DMILL_BUILD_LIBRARIES=" + genIdeaArgs.map(_.path).mkString(","),
        "-DMILL_SCALA_LIB=" + runClasspath().map(_.path).mkString(","),
        s"-DTEST_SCALAFMT_VERSION=${Deps.scalafmtDynamic.dep.version}"
      )
  }
  object backgroundwrapper extends MillPublishModule {
    override def ivyDeps = Agg(
      Deps.sbtTestInterface
    )
    def testArgs = T {
      Seq(
        "-DMILL_BACKGROUNDWRAPPER=" + runClasspath().map(_.path).mkString(",")
      )
    }
  }
  object api extends MillApiModule {
    override def moduleDeps = Seq(main.api)
  }
  object worker extends MillInternalModule {

    override def moduleDeps = Seq(scalalib.api)

    override def ivyDeps = Agg(
      Deps.zinc,
      Deps.log4j2Core
    )
    def testArgs = T {
      Seq(
        "-DMILL_SCALA_WORKER=" + runClasspath().map(_.path).mkString(",")
      )
    }

    override def generatedSources = T {
      val dest = T.ctx.dest
      val artifacts = T.traverse(dev.moduleDeps)(_.publishSelfDependency)()
      os.write(
        dest / "Versions.scala",
        s"""package mill.scalalib.worker
           |
           |/**
           | * Dependency versions.
           | * Generated from mill in build.sc.
           | */
           |object Versions {
           |  /** Version of Zinc. */
           |  val zinc = "${Deps.zinc.dep.version}"
           |}
           |
           |""".stripMargin
      )
      super.generatedSources() ++ Seq(PathRef(dest))
    }
  }
}

object scalajslib extends MillModule {

  override def moduleDeps = Seq(scalalib, scalajslib.`worker-api`)

  override def testArgs = T {
    val mapping = Map(
      "MILL_SCALAJS_WORKER_1" -> worker("1").compile().classes.path
    )
    Seq("-Djna.nosys=true") ++
      scalalib.worker.testArgs() ++
      scalalib.backgroundwrapper.testArgs() ++
      (for ((k, v) <- mapping.to(Seq)) yield s"-D$k=$v")
  }

  def generatedBuildInfo = T {
    val dir = T.dest
    val resolve = resolveCoursierDependency()
    val packageNames = Seq("mill", "scalajslib")
    val className = "ScalaJSBuildInfo"
    def formatDep(dep: Dep) = {
      val d = resolve(dep)
      s"${d.module.organization.value}:${d.module.name.value}:${d.version}"
    }
    val content =
      s"""package ${packageNames.mkString(".")}
         |/** Generated by mill at built-time. */
         |object ${className} {
         |  object Deps {
         |    @deprecated("No longer a dependency. To be removed.", since = "mill 0.10.9")
         |    val jettyWebsocket = "org.eclipse.jetty:jetty-websocket:8.2.0.v20160908"
         |    @deprecated("No longer a dependency. To be removed.", since = "mill 0.10.9")
         |    val jettyServer = "org.eclipse.jetty:jetty-server:8.2.0.v20160908"
         |    @deprecated("No longer a dependency. To be removed.", since = "mill 0.10.9")
         |    val javaxServlet = "org.eclipse.jetty.orbit:javax.servlet:3.0.0.v201112011016"
         |    val scalajsEnvNodejs = "${formatDep(Deps.Scalajs_1.scalajsEnvNodejs)}"
         |    val scalajsEnvJsdomNodejs = "${formatDep(Deps.Scalajs_1.scalajsEnvJsdomNodejs)}"
         |    val scalajsEnvExoegoJsdomNodejs = "${formatDep(
          Deps.Scalajs_1.scalajsEnvExoegoJsdomNodejs
        )}"
         |    val scalajsEnvPhantomJs = "${formatDep(Deps.Scalajs_1.scalajsEnvPhantomjs)}"
         |  }
         |}
         |""".stripMargin
    os.write(dir / packageNames / s"${className}.scala", content, createFolders = true)
    PathRef(dir)
  }

  override def generatedSources: Target[Seq[PathRef]] = Seq(generatedBuildInfo())

  object `worker-api` extends MillInternalModule {
    override def ivyDeps = Agg(Deps.sbtTestInterface)
  }
  object worker extends Cross[WorkerModule]("1")
  class WorkerModule(scalajsWorkerVersion: String) extends MillInternalModule {
    override def moduleDeps = Seq(scalajslib.`worker-api`)
    override def ivyDeps = Agg(
      Deps.Scalajs_1.scalajsLinker,
      Deps.Scalajs_1.scalajsSbtTestAdapter,
      Deps.Scalajs_1.scalajsEnvNodejs,
      Deps.Scalajs_1.scalajsEnvJsdomNodejs,
      Deps.Scalajs_1.scalajsEnvExoegoJsdomNodejs,
      Deps.Scalajs_1.scalajsEnvPhantomjs
    )
  }
}

object contrib extends MillModule {
  object testng extends JavaModule with MillModule {
    // pure Java implementation
    override def artifactSuffix: T[String] = ""
    override def scalaLibraryIvyDeps: Target[Agg[Dep]] = T { Agg.empty[Dep] }
    override def ivyDeps = Agg(Deps.sbtTestInterface)
    override def compileIvyDeps = Agg(Deps.testng)
    override def runIvyDeps = Agg(Deps.testng)
    override def testArgs = T {
      Seq(
        "-DMILL_SCALA_LIB=" + scalalib.runClasspath().map(_.path).mkString(","),
        "-DMILL_TESTNG_LIB=" + runClasspath().map(_.path).mkString(",")
      ) ++ scalalib.worker.testArgs()
    }
    override def testModuleDeps: Seq[JavaModule] = super.testModuleDeps ++ Seq(
      scalalib
    )
    override def docJar: T[PathRef] = super[JavaModule].docJar
  }

  object twirllib extends MillModule {
    override def compileModuleDeps = Seq(scalalib)
    override def testModuleDeps: Seq[JavaModule] = super.testModuleDeps ++ Seq(scalalib)
  }

  object playlib extends MillModule {
    override def moduleDeps = Seq(twirllib, playlib.api)
    override def compileModuleDeps = Seq(scalalib)

    override def testArgs = T {
      val mapping = Map(
        "MILL_CONTRIB_PLAYLIB_ROUTECOMPILER_WORKER_2_6" -> worker("2.6").assembly().path,
        "MILL_CONTRIB_PLAYLIB_ROUTECOMPILER_WORKER_2_7" -> worker("2.7").assembly().path,
        "MILL_CONTRIB_PLAYLIB_ROUTECOMPILER_WORKER_2_8" -> worker("2.8").assembly().path,
        "TEST_PLAY_VERSION_2_6" -> Deps.Play_2_6.playVersion,
        "TEST_PLAY_VERSION_2_7" -> Deps.Play_2_7.playVersion,
        "TEST_PLAY_VERSION_2_8" -> Deps.Play_2_8.playVersion
      )

      scalalib.worker.testArgs() ++
        scalalib.backgroundwrapper.testArgs() ++
        (for ((k, v) <- mapping.to(Seq)) yield s"-D$k=$v")
    }
    override def testModuleDeps: Seq[JavaModule] = super.testModuleDeps ++ Seq(scalalib)

    object api extends MillPublishModule

    object worker extends Cross[WorkerModule](Deps.play.keys.toSeq: _*)
    class WorkerModule(playBinary: String) extends MillInternalModule {
      override def sources = T.sources {
        // We want to avoid duplicating code as long as the Play APIs allow.
        // But if newer Play versions introduce incompatibilities,
        // just remove the shared source dir for that worker and implement directly.
        Seq(PathRef(millSourcePath / os.up / "src-shared")) ++ super.sources()
      }
      override def scalaVersion = Deps.play(playBinary).scalaVersion
      override def moduleDeps = Seq(playlib.api)
      override def ivyDeps = Agg(
        Deps.osLib,
        Deps.play(playBinary).routesCompiler
      )
    }
  }

  object scalapblib extends MillModule {
    override def compileModuleDeps = Seq(scalalib)
    override def testModuleDeps: Seq[JavaModule] = super.testModuleDeps ++ Seq(scalalib)
  }

  object scoverage extends MillModule {
    object api extends MillApiModule {
      override def compileModuleDeps = Seq(main.api)
    }
    override def moduleDeps = Seq(scoverage.api)
    override def compileModuleDeps = Seq(scalalib)

    override def testArgs = T {
      val mapping = Map(
        "MILL_SCOVERAGE_REPORT_WORKER" -> worker.compile().classes.path,
        "MILL_SCOVERAGE2_REPORT_WORKER" -> worker2.compile().classes.path,
        "MILL_SCOVERAGE_VERSION" -> Deps.scalacScoveragePlugin.dep.version,
        "MILL_SCOVERAGE2_VERSION" -> Deps.scalacScoverage2Plugin.dep.version,
        "TEST_SCALA_2_12_VERSION" -> "2.12.15" // last supported 2.12 version for Scoverage 1.x
      )
      scalalib.worker.testArgs() ++
        scalalib.backgroundwrapper.testArgs() ++
        (for ((k, v) <- mapping) yield s"-D$k=$v")
    }

    // So we can test with buildinfo in the classpath
    override def testModuleDeps: Seq[JavaModule] = super.testModuleDeps ++ Seq(
      scalalib,
      contrib.buildinfo
    )

    // Worker for Scoverage 1.x
    object worker extends MillInternalModule {
      override def compileModuleDeps = Seq(main.api)
      override def moduleDeps = Seq(scoverage.api)
      override def compileIvyDeps = T {
        Agg(
          // compile-time only, need to provide the correct scoverage version at runtime
          Deps.scalacScoveragePlugin,
          // provided by mill runtime
          Deps.osLib
        )
      }
      override def scalaVersion: Target[String] = Deps.scalaVersionForScoverageWorker1
    }

    // Worker for Scoverage 2.0
    object worker2 extends MillInternalModule {
      override def compileModuleDeps = Seq(main.api)
      override def moduleDeps = Seq(scoverage.api)
      override def compileIvyDeps = T {
        Agg(
          // compile-time only, need to provide the correct scoverage version at runtime
          Deps.scalacScoverage2Plugin,
          Deps.scalacScoverage2Reporter,
          Deps.scalacScoverage2Domain,
          Deps.scalacScoverage2Serializer,
          // provided by mill runtime
          Deps.osLib
        )
      }
    }
  }

  object buildinfo extends MillModule {
    override def compileModuleDeps = Seq(scalalib)
    // why do I need this?
    override def testArgs = T {
      Seq("-Djna.nosys=true") ++
        scalalib.worker.testArgs() ++
        scalalib.backgroundwrapper.testArgs()
    }
    override def testModuleDeps: Seq[JavaModule] = super.testModuleDeps ++ Seq(scalalib)
  }

  object proguard extends MillModule {
    override def compileModuleDeps = Seq(scalalib)
    override def testArgs = T {
      Seq(
        "-DMILL_SCALA_LIB=" + scalalib.runClasspath().map(_.path).mkString(","),
        "-DMILL_PROGUARD_LIB=" + runClasspath().map(_.path).mkString(",")
      ) ++ scalalib.worker.testArgs()
    }
    override def testModuleDeps: Seq[JavaModule] = super.testModuleDeps ++ Seq(scalalib)
  }

  object flyway extends MillModule {
    override def compileModuleDeps = Seq(scalalib)
    override def ivyDeps = Agg(Deps.flywayCore)
    override def testModuleDeps: Seq[JavaModule] = super.testModuleDeps ++ Seq(scalalib)
  }

  object docker extends MillModule {
    override def compileModuleDeps = Seq(scalalib)
    override def testArgs = T {
      Seq("-Djna.nosys=true") ++
        scalalib.worker.testArgs() ++
        scalalib.backgroundwrapper.testArgs()
    }
    override def testModuleDeps: Seq[JavaModule] = super.testModuleDeps ++ Seq(scalalib)
  }

  object bloop extends MillModule {
    override def compileModuleDeps = Seq(scalalib, scalajslib, scalanativelib)
    override def ivyDeps = Agg(
      Deps.bloopConfig.exclude("*" -> s"jsoniter-scala-core_2.13")
    )
    override def testArgs = T(scalanativelib.testArgs())
    override def testModuleDeps: Seq[JavaModule] = super.testModuleDeps ++ Seq(
      scalalib,
      scalajslib,
      scalanativelib
    )
    def generateBuildinfo = T {
      os.write(
        T.dest / "Versions.scala",
        s"""package mill.contrib.bloop
           |
           |object Versions {
           |  val bloop = "${Deps.bloopConfig.dep.version}"
           |}
           |""".stripMargin
      )
      PathRef(T.dest)
    }
    override def generatedSources = T {
      super.generatedSources() ++ Seq(generateBuildinfo())
    }
  }

  object artifactory extends MillModule {
    override def compileModuleDeps = Seq(scalalib)
    override def ivyDeps = T { Agg(Deps.requests) }
  }

  object codeartifact extends MillModule {
    override def compileModuleDeps = Seq(scalalib)
    override def ivyDeps = T { Agg(Deps.requests) }
  }

  object versionfile extends MillModule {
    override def compileModuleDeps = Seq(scalalib)
  }

  object bintray extends MillModule {
    override def compileModuleDeps = Seq(scalalib)
    override def ivyDeps = T { Agg(Deps.requests) }
  }

  object gitlab extends MillInternalModule with MillAutoTestSetup {
    override def compileModuleDeps = Seq(scalalib)
    override def ivyDeps = T { Agg(Deps.requests, Deps.osLib) }

    override def testModuleDeps: Seq[JavaModule] = super.testModuleDeps ++ Seq(
      scalalib
    )
  }

  object jmh extends MillInternalModule with MillAutoTestSetup with WithMillCompiler {
    override def compileModuleDeps = Seq(scalalib)
    override def testArgs = T {
      Seq(
        "-DMILL_SCALA_LIB=" + scalalib.runClasspath().map(_.path).mkString(",")
      ) ++ scalalib.worker.testArgs()
    }
    override def testModuleDeps: Seq[JavaModule] = super.testModuleDeps ++ Seq(scalalib)
  }
}

object scalanativelib extends MillModule {
  override def moduleDeps = Seq(scalalib, scalanativelib.`worker-api`)

  override def testArgs = T {
    val mapping = Map(
      "MILL_SCALANATIVE_WORKER_0_4" -> worker("0.4").compile().classes.path
    )
    scalalib.worker.testArgs() ++
      scalalib.backgroundwrapper.testArgs() ++
      (for ((k, v) <- mapping.to(Seq)) yield s"-D$k=$v")
  }

  object `worker-api` extends MillInternalModule {
    override def ivyDeps = Agg(Deps.sbtTestInterface)
  }
  object worker extends Cross[WorkerModule]("0.4")
  class WorkerModule(scalaNativeWorkerVersion: String)
      extends MillInternalModule {
    override def moduleDeps = Seq(scalanativelib.`worker-api`)
    override def ivyDeps = scalaNativeWorkerVersion match {
      case "0.4" =>
        Agg(
          Deps.osLib,
          Deps.Scalanative_0_4.scalanativeTools,
          Deps.Scalanative_0_4.scalanativeUtil,
          Deps.Scalanative_0_4.scalanativeNir,
          Deps.Scalanative_0_4.scalanativeTestRunner
        )
    }
  }
}

object bsp extends MillModule {
  override def compileModuleDeps = Seq(scalalib)
  override def testModuleDeps: Seq[JavaModule] = super.testModuleDeps ++ compileModuleDeps

  def generatedBuildInfo: T[Seq[PathRef]] = T {
    val workerDep = worker.publishSelfDependency()
    val code =
      s"""// Generated by Mill
         |package mill.bsp
         |
         |/** Build-time information. */
         |object BuildInfo {
         |  /** BSP4j version (BSP Protocol version). */
         |  val bsp4jVersion = "${Deps.bsp4j.dep.version}"
         |  /** BSP worker dependency. */
         |  val millBspWorkerDep = "${workerDep.group}:${workerDep.id}:${workerDep.version}"
         |}
         |""".stripMargin.trim
    os.write(T.dest / "mill" / "bsp" / "BuildInfo.scala", code, createFolders = true)
    Seq(PathRef(T.dest))
  }
  override def generatedSources: T[Seq[PathRef]] =
    super.generatedSources() ++ generatedBuildInfo()

  override val test = new Test(implicitly)
  class Test(ctx0: mill.define.Ctx) extends Tests(ctx0) {
    override def forkEnv: Target[Map[String, String]] = T {
      // We try to fetch this dependency with coursier in the tests
      bsp.worker.publishLocal()()
      super.forkEnv()
    }
  }

  object worker extends MillInternalModule {
    override def compileModuleDeps = Seq(bsp, scalalib, testrunner)
    override def ivyDeps = Agg(
      Deps.bsp4j,
      Deps.sbtTestInterface
    )

    def generatedBuildInfo: T[Seq[PathRef]] = T {
      val workerDep = worker.publishSelfDependency()
      val code =
        s"""// Generated by Mill
           |package mill.bsp.worker
           |
           |/** Build-time information. */
           |object BuildInfo {
           |  /** BSP4j version (BSP Protocol version). */
           |  val bsp4jVersion = "${Deps.bsp4j.dep.version}"
           |  /** BSP worker dependency. */
           |  val millBspWorkerVersion = "${workerDep.version}"
           |}
           |""".stripMargin.trim
      os.write(T.dest / "mill" / "bsp" / "worker" / "BuildInfo.scala", code, createFolders = true)
      Seq(PathRef(T.dest))
    }
    override def generatedSources: T[Seq[PathRef]] =
      super.generatedSources() ++ generatedBuildInfo()
  }
}

val DefaultLocalMillReleasePath =
  s"target/mill-release${if (scala.util.Properties.isWin) ".bat" else ""}"

/**
 * Build and install Mill locally.
 * @param binFile The location where the Mill binary should be installed
 * @param ivyRepo The local Ivy repository where Mill modules should be published to
 */
def installLocal(binFile: String = DefaultLocalMillReleasePath, ivyRepo: String = null) =
  T.command {
    PathRef(installLocalTask(T.task(binFile), ivyRepo)())
  }

def installLocalTask(binFile: Task[String], ivyRepo: String = null): Task[os.Path] = {
  val modules = build.millInternal.modules.collect { case m: PublishModule => m }
  T.task {
    T.traverse(modules)(m => m.publishLocal(ivyRepo))()
    val millBin = assembly()
    val targetFile = os.Path(binFile(), T.workspace)
    if (os.exists(targetFile))
      T.log.info(s"Overwriting existing local Mill binary at ${targetFile}")
    os.copy.over(millBin.path, targetFile, createFolders = true)
    T.log.info(s"Published ${modules.size} modules and installed ${targetFile}")
    targetFile
  }
}

object integration extends MillScalaModule {
  override def moduleDeps = Seq(scalalib, scalajslib, scalanativelib)

  /** Deploy freshly build mill for use in tests */
  def testMill: Target[PathRef] = {
    val name = if (scala.util.Properties.isWin) "mill.bat" else "mill"
    T { PathRef(installLocalTask(binFile = T.task((T.dest / name).toString()))()) }
  }

  trait ITests extends super.Tests {
    def workspaceDir = T.persistent { PathRef(T.dest) }
    override def forkArgs: Target[Seq[String]] = T {
      super.forkArgs() ++
        scalajslib.testArgs() ++
        scalalib.worker.testArgs() ++
        scalalib.backgroundwrapper.testArgs() ++
        scalanativelib.testArgs() ++
        Seq(
          s"-DMILL_WORKSPACE_PATH=${workspaceDir().path}",
          s"-DMILL_TESTNG=${contrib.testng.runClasspath().map(_.path).mkString(",")}",
          s"-DMILL_VERSION=${millVersion()}",
          s"-DMILL_SCALA_LIB=${scalalib.runClasspath().map(_.path).mkString(",")}",
          "-Djna.nosys=true"
        )
    }
  }

  // Integration test of Mill
  object local extends ITests
  trait Forked extends ITests {
    override def moduleDeps: Seq[JavaModule] = super.moduleDeps ++ Seq(integration.local)

    override def forkEnv: Target[Map[String, String]] = super.forkEnv() ++ Map(
      "MILL_TEST_RELEASE" -> testMill().path.toString()
    )
  }
  object forked extends Forked
  object `forked-server` extends Forked

  // Test of various third-party repositories
  object thirdparty extends Module {
    def testRepos = T {
      Seq(
        "MILL_ACYCLIC_REPO" ->
          shared.downloadTestRepo(
            "lihaoyi/acyclic",
            "bc41cd09a287e2c270271e27ccdb3066173a8598",
            T.dest / "acyclic"
          ),
        "MILL_JAWN_REPO" ->
          shared.downloadTestRepo(
            "non/jawn",
            "fd8dc2b41ce70269889320aeabf8614fe1e8fbcb",
            T.dest / "jawn"
          ),
        "MILL_AMMONITE_REPO" ->
          shared.downloadTestRepo(
            "lihaoyi/ammonite",
            "26b7ebcace16b4b5b4b68f9344ea6f6f48d9b53e",
            T.dest / "ammonite"
          ),
        "MILL_UPICKLE_REPO" ->
          shared.downloadTestRepo(
            "lihaoyi/upickle",
            "7f33085c890db7550a226c349832eabc3cd18769",
            T.dest / "upickle"
          ),
        "MILL_PLAY_JSON_REPO" ->
          shared.downloadTestRepo(
            "playframework/play-json",
            "0a5ba16a03f3b343ac335117eb314e7713366fd4",
            T.dest / "play-json"
          ),
        "MILL_CAFFEINE_REPO" ->
          shared.downloadTestRepo(
            "ben-manes/caffeine",
            "c02c623aedded8174030596989769c2fecb82fe4",
            T.dest / "caffeine"
          )
      )
    }

    object local extends ITests {
      override def forkArgs: Target[Seq[String]] = T {
        super.forkArgs() ++ (for ((k, v) <- testRepos()) yield s"-D$k=$v")
      }

      override def runClasspath: T[Seq[PathRef]] = T {
        // we need to trigger installation of testng-contrib for Caffeine
        contrib.testng.publishLocal()()
        super.runClasspath()
      }
    }
    object forked extends ITests {
      override def moduleDeps: Seq[JavaModule] =
        super.moduleDeps ++ Seq(integration.thirdparty.local)
      override def forkEnv: Target[Map[String, String]] = super.forkEnv() ++ Map(
        "MILL_TEST_RELEASE" -> testMill().path.toString()
      )
      override def forkArgs: Target[Seq[String]] = T {
        super.forkArgs() ++ (for ((k, v) <- testRepos()) yield s"-D$k=$v")
      }
    }
  }
}

def launcherScript(
    shellJvmArgs: Seq[String],
    cmdJvmArgs: Seq[String],
    shellClassPath: Agg[String],
    cmdClassPath: Agg[String]
) = {
  val millMainClass = "mill.main.client.MillClientMain"
  val millClientMainClass = "mill.main.client.MillClientMain"

  mill.modules.Jvm.universalScript(
    shellCommands = {
      val jvmArgsStr = shellJvmArgs.mkString(" ")
      def java(mainClass: String, passMillJvmOpts: Boolean) = {
        val millJvmOpts = if (passMillJvmOpts) "$mill_jvm_opts" else ""
        s"""exec "$$JAVACMD" $jvmArgsStr $$JAVA_OPTS $millJvmOpts -cp "${shellClassPath.mkString(
            ":"
          )}" $mainClass "$$@""""
      }

      s"""if [ -z "$$JAVA_HOME" ] ; then
         |  JAVACMD="java"
         |else
         |  JAVACMD="$$JAVA_HOME/bin/java"
         |fi
         |
         |mill_jvm_opts=""
         |init_mill_jvm_opts () {
         |  if [ -z $$MILL_JVM_OPTS_PATH ] ; then
         |    mill_jvm_opts_file=".mill-jvm-opts"
         |  else
         |    mill_jvm_opts_file=$$MILL_JVM_OPTS_PATH
         |  fi
         |
         |  if [ -f "$$mill_jvm_opts_file" ] ; then
         |    # We need to append a newline at the end to fix
         |    # https://github.com/com-lihaoyi/mill/issues/2140
         |    newline="
         |"
         |    mill_jvm_opts="$$(
         |      echo "$$newline" | cat "$$mill_jvm_opts_file" - | (
         |        while IFS= read line
         |        do
         |          mill_jvm_opts="$${mill_jvm_opts} $$(echo $$line | grep -v "^[[:space:]]*[#]")"
         |        done
         |        # we are in a sub-shell, so need to return it explicitly
         |        echo "$${mill_jvm_opts}"
         |      )
         |    )"
         |    mill_jvm_opts="$${mill_jvm_opts} -Dmill.jvm_opts_applied=true"
         |  fi
         |}
         |
         |# Client-server mode doesn't seem to work on WSL, just disable it for now
         |# https://stackoverflow.com/a/43618657/871202
         |if grep -qEi "(Microsoft|WSL)" /proc/version > /dev/null 2> /dev/null ; then
         |    init_mill_jvm_opts
         |    if [ -z $$COURSIER_CACHE ] ; then
         |      COURSIER_CACHE=.coursier
         |    fi
         |    ${java(millMainClass, true)}
         |else
         |    case "$$1" in
         |      -i | --interactive | --repl | --no-server | --bsp )
         |        init_mill_jvm_opts
         |        ${java(millMainClass, true)}
         |        ;;
         |      *)
         |        ${java(millClientMainClass, false)}
         |        ;;
         |esac
         |fi
         |""".stripMargin
    },
    cmdCommands = {
      val jvmArgsStr = cmdJvmArgs.mkString(" ")
      def java(mainClass: String, passMillJvmOpts: Boolean) = {
        val millJvmOpts = if (passMillJvmOpts) "!mill_jvm_opts!" else ""
        s""""%JAVACMD%" $jvmArgsStr %JAVA_OPTS% $millJvmOpts -cp "${cmdClassPath.mkString(
            ";"
          )}" $mainClass %*"""
      }

      s"""setlocal EnableDelayedExpansion
         |set "JAVACMD=java.exe"
         |if not "%JAVA_HOME%"=="" set "JAVACMD=%JAVA_HOME%\\bin\\java.exe"
         |if "%1" == "-i" set _I_=true
         |if "%1" == "--interactive" set _I_=true
         |if "%1" == "--repl" set _I_=true
         |if "%1" == "--no-server" set _I_=true
         |if "%1" == "--bsp" set _I_=true
         |
         |set "mill_jvm_opts="
         |set "mill_jvm_opts_file=.mill-jvm-opts"
         |if not "%MILL_JVM_OPTS_PATH%"=="" set "mill_jvm_opts_file=%MILL_JVM_OPTS_PATH%"
         |
         |if defined _I_ (
         |  if exist %mill_jvm_opts_file% (
         |    for /f "delims=" %%G in (%mill_jvm_opts_file%) do (
         |      set line=%%G
         |      if "!line:~0,2!"=="-X" set "mill_jvm_opts=!mill_jvm_opts! !line!"
         |    )
         |  )
         |  ${java(millMainClass, true)}
         |) else (
         |  ${java(millClientMainClass, false)}
         |)
         |endlocal
         |""".stripMargin
    }
  )
}

object dev extends MillModule {
  override def moduleDeps = Seq(scalalib, scalajslib, scalanativelib, bsp)

  def forkArgs: T[Seq[String]] =
    (
      scalalib.testArgs() ++
        scalajslib.testArgs() ++
        scalalib.worker.testArgs() ++
        scalanativelib.testArgs() ++
        scalalib.backgroundwrapper.testArgs() ++
        // Workaround for Zinc/JNA bug
        // https://github.com/sbt/sbt/blame/6718803ee6023ab041b045a6988fafcfae9d15b5/main/src/main/scala/sbt/Main.scala#L130
        Seq(
          "-Djna.nosys=true",
          "-DMILL_VERSION=" + publishVersion(),
          "-DMILL_CLASSPATH=" + runClasspath().map(_.path.toString).mkString(",")
        )
    ).distinct

  override def launcher = T {
    val isWin = scala.util.Properties.isWin
    val outputPath = T.ctx.dest / (if (isWin) "run.bat" else "run")

    os.write(outputPath, prependShellScript())

    if (!isWin) {
      os.perms.set(outputPath, "rwxrwxrwx")
    }
    PathRef(outputPath)
  }

  override def extraPublish: T[Seq[PublishInfo]] = T {
    Seq(
      PublishInfo(file = assembly(), classifier = Some("assembly"), ivyConfig = "compile")
    )
  }

  override def assembly = T {
    val isWin = scala.util.Properties.isWin
    val millPath = T.ctx.dest / (if (isWin) "mill.bat" else "mill")
    os.copy(super.assembly().path, millPath)
    PathRef(millPath)
  }

  def prependShellScript = T {
    val (millArgs, otherArgs) =
      forkArgs().partition(arg => arg.startsWith("-DMILL") && !arg.startsWith("-DMILL_VERSION"))
    // Pass Mill options via file, due to small max args limit in Windows
    val vmOptionsFile = T.ctx.dest / "mill.properties"
    val millOptionsContent =
      millArgs.map(_.drop(2).replace("\\", "/")).mkString(
        "\r\n"
      ) // drop -D prefix, replace \ with /
    os.write(vmOptionsFile, millOptionsContent)
    val jvmArgs = otherArgs ++ List(s"-DMILL_OPTIONS_PATH=$vmOptionsFile")
    val classpath = runClasspath().map(_.path.toString)
    launcherScript(
      jvmArgs,
      jvmArgs,
      classpath,
      Agg(pathingJar().path.toString) // TODO not working yet on Windows! see #791
    )
  }

  def pathingJar = T {
    // see http://todayguesswhat.blogspot.com/2011/03/jar-manifestmf-class-path-referencing.html
    // for more detailed explanation
    val isWin = scala.util.Properties.isWin
    val classpath = runClasspath().map { pathRef =>
      val path =
        if (isWin) "/" + pathRef.path.toString.replace("\\", "/")
        else pathRef.path.toString
      if (path.endsWith(".jar")) path
      else path + "/"
    }.mkString(" ")
    val manifestEntries = Map[String, String](
      java.util.jar.Attributes.Name.MANIFEST_VERSION.toString -> "1.0",
      "Created-By" -> "Scala mill",
      "Class-Path" -> classpath
    )
    mill.modules.Jvm.createJar(Agg(), mill.modules.Jvm.JarManifest(manifestEntries))
  }

  def run(args: String*) = T.command {
    args match {
      case Nil => mill.api.Result.Failure("Need to pass in cwd as first argument to dev.run")
      case wd0 +: rest =>
        val wd = os.Path(wd0, T.workspace)
        os.makeDir.all(wd)
        mill.modules.Jvm.runSubprocess(
          Seq(launcher().path.toString) ++ rest,
          forkEnv(),
          workingDir = wd
        )
        mill.api.Result.Success(())
    }

  }
}

object docs extends Module {

  /** Generates the mill documentation with Antora. */
  object antora extends Module {
    private val npmExe = if (scala.util.Properties.isWin) "npm.cmd" else "npm"
    private val antoraExe = if (scala.util.Properties.isWin) "antora.cmd" else "antora"
    def npmBase: T[os.Path] = T.persistent { T.dest }
    def prepareAntora(npmDir: os.Path) = {
      Jvm.runSubprocess(
        commandArgs = Seq(
          npmExe,
          "install",
          "@antora/cli@3.0.1",
          "@antora/site-generator-default@3.0.1",
          "gitlab:antora/xref-validator",
          "@antora/lunr-extension@v1.0.0-alpha.6"
        ),
        envArgs = Map(),
        workingDir = npmDir
      )
    }
    def runAntora(npmDir: os.Path, workDir: os.Path, args: Seq[String])(implicit
        ctx: mill.api.Ctx.Log
    ) = {
      prepareAntora(npmDir)
      val cmdArgs =
        Seq(s"${npmDir}/node_modules/.bin/${antoraExe}") ++ args
      ctx.log.debug(s"command: ${cmdArgs.mkString("'", "' '", "'")}")
      Jvm.runSubprocess(
        commandArgs = cmdArgs,
        envArgs = Map("CI" -> "true"),
        workingDir = workDir
      )
      PathRef(workDir / "build" / "site")
    }
    def source: Source = T.source(millSourcePath)
    def supplementalFiles = T.source(millSourcePath / "supplemental-ui")
    def devAntoraSources: Target[PathRef] = T {
      val dest = T.dest
      shared.mycopy(source().path, dest, mergeFolders = true)
      val lines = os.read(dest / "antora.yml").linesIterator.map {
        case l if l.startsWith("version:") =>
          s"version: 'master'" + "\n" + s"display-version: '${millVersion()}'"
        case l if l.startsWith("    mill-version:") =>
          s"    mill-version: '${millVersion()}'"
        case l if l.startsWith("    mill-last-tag:") =>
          s"    mill-last-tag: '${millLastTag()}'"
        case l => l
      }
      os.write.over(dest / "antora.yml", lines.mkString("\n"))
      PathRef(dest)
    }
    def githubPagesPlaybookText(authorMode: Boolean): Task[String] = T.task {
      s"""site:
         |  title: Mill
         |  url: ${Settings.docUrl}
         |  start_page: mill::Intro_to_Mill.adoc
         |
         |content:
         |  sources:
         |    - url: ${if (authorMode) baseDir else Settings.projectUrl}
         |      branches: ${if (Settings.docBranches.isEmpty) "~"
        else Settings.docBranches.map("'" + _ + "'").mkString("[", ",", "]")}
         |      tags: ${Settings.docTags.map("'" + _ + "'").mkString("[", ",", "]")}
         |      start_path: docs/antora
         |    # the master documentation (always in author mode)
         |    - url: ${baseDir}
         |      # edit_url: ${Settings.projectUrl}/edit/{refname}/{path}
         |      branches: HEAD
         |      start_path: ${devAntoraSources().path.relativeTo(baseDir)}
         |ui:
         |  bundle:
         |    url: https://gitlab.com/antora/antora-ui-default/-/jobs/artifacts/master/raw/build/ui-bundle.zip?job=bundle-stable
         |    snapshot: true
         |  supplemental_files: ${supplementalFiles().path.toString()}
         |
         |asciidoc:
         |  attributes:
         |    mill-github-url: ${Settings.projectUrl}
         |    mill-doc-url: ${Settings.docUrl}
         |    utest-github-url: https://github.com/com-lihaoyi/utest
         |    upickle-github-url: https://github.com/com-lihaoyi/upickle
         |
         |antora:
         |  extensions:
         |  - require: '@antora/lunr-extension'
         |    index_latest_only: true
         |
         |""".stripMargin
    }
    def githubPages: Target[PathRef] = T {
      generatePages(authorMode = false)()
    }
    def localPages = T {
      val pages = generatePages(authorMode = true)()
      T.log.outputStream.println(
        s"You can browse the local pages at: ${(pages.path / "index.html").toNIO.toUri()}"
      )
    }
    def generatePages(authorMode: Boolean) = T.task {
      // dependency to sources
      source()
      val docSite = T.dest
      val playbook = docSite / "antora-playbook.yml"
      val siteDir = docSite / "site"
      os.write(
        target = playbook,
        data = githubPagesPlaybookText(authorMode)(),
        createFolders = true
      )
      // check xrefs
      runAntora(
        npmDir = npmBase(),
        workDir = docSite,
        args = Seq(
          "--generator",
          "@antora/xref-validator",
          playbook.last,
          "--to-dir",
          siteDir.toString(),
          "--attribute",
          "page-pagination"
        ) ++
          Seq("--fetch").filter(_ => !authorMode)
      )
      // generate site (we can skip the --fetch now)
      runAntora(
        npmDir = npmBase(),
        workDir = docSite,
        args = Seq(
          playbook.last,
          "--to-dir",
          siteDir.toString(),
          "--attribute",
          "page-pagination"
        )
      )
      os.write(siteDir / ".nojekyll", "")
      // sanitize devAntora source URLs
      sanitizeDevUrls(siteDir, devAntoraSources().path, source().path, baseDir)
      PathRef(siteDir)
    }
//    def htmlCleanerIvyDeps = T{ Agg(ivy"net.sourceforge.htmlcleaner:htmlcleaner:2.24")}
    def sanitizeDevUrls(
        dir: os.Path,
        sourceDir: os.Path,
        newSourceDir: os.Path,
        baseDir: os.Path
    ): Unit = {
      val pathToRemove = sourceDir.relativeTo(baseDir).toString()
      val replacePath = newSourceDir.relativeTo(baseDir).toString()
//      println(s"Cleaning relative path '${pathToRemove}' ...")
      import org.htmlcleaner._
      val cleaner = new HtmlCleaner()
      var changed = false
      os.walk(dir).foreach { file =>
        if (os.isFile(file) && file.ext == "html") {
          val node: TagNode = cleaner.clean(file.toIO)
          node.traverse { (parentNode: TagNode, htmlNode: HtmlNode) =>
            htmlNode match {
              case tag: TagNode if tag.getName() == "a" =>
                Option(tag.getAttributeByName("href")).foreach { href =>
                  val newHref = href.replace(pathToRemove, replacePath)
                  if (href != newHref) {
                    tag.removeAttribute("href")
                    tag.addAttribute("href", newHref)
                    changed = true
                    println(s"Replaced: '${href}' --> '${newHref}'")
                  }
                }
                true
              case _ => true
            }
          }
          if (changed) {
            println(s"Writing '${file}' ...")
            val newHtml = new SimpleHtmlSerializer(cleaner.getProperties()).getAsString(node)
            os.write.over(file, newHtml)
          }
        }
      }
    }
  }
}

def assembly = T {
  val version = millVersion()
  val devRunClasspath = dev.runClasspath().map(_.path)
  val filename = if (scala.util.Properties.isWin) "mill.bat" else "mill"
  val commonArgs = Seq(
    "-DMILL_VERSION=" + version,
    // Workaround for Zinc/JNA bug
    // https://github.com/sbt/sbt/blame/6718803ee6023ab041b045a6988fafcfae9d15b5/main/src/main/scala/sbt/Main.scala#L130
    "-Djna.nosys=true"
  )
  val shellArgs = Seq("-DMILL_CLASSPATH=$0") ++ commonArgs
  val cmdArgs = Seq(""""-DMILL_CLASSPATH=%~dpnx0"""") ++ commonArgs
  os.move(
    Jvm.createAssembly(
      devRunClasspath,
      prependShellScript = launcherScript(
        shellArgs,
        cmdArgs,
        Agg("$0"),
        Agg("%~dpnx0")
      )
    ).path,
    T.ctx.dest / filename
  )
  PathRef(T.ctx.dest / filename)
}

def millBootstrap = T.sources(T.workspace / "mill")

def launcher = T {
  val outputPath = T.ctx.dest / "mill"
  val millBootstrapGrepPrefix = "\nDEFAULT_MILL_VERSION="
  os.write(
    outputPath,
    os.read(millBootstrap().head.path)
      .replaceAll(
        millBootstrapGrepPrefix + "[^\\n]+",
        millBootstrapGrepPrefix + millVersion()
      )
  )
  os.perms.set(outputPath, "rwxrwxrwx")
  PathRef(outputPath)
}

def uploadToGithub(authKey: String) = T.command {
  val vcsState = VcsVersion.vcsState()
  val label = vcsState.format()
  if (label != millVersion()) sys.error("Modified mill version detected, aborting upload")
  val releaseTag = vcsState.lastTag.getOrElse(sys.error(
    "Incomplete git history. No tag found.\nIf on CI, make sure your git checkout job includes enough history."
  ))

  if (releaseTag == label) {
    // TODO: check if the tag already exists (e.g. because we created it manually) and do not fail
    scalaj.http.Http(
      s"https://api.github.com/repos/${Settings.githubOrg}/${Settings.githubRepo}/releases"
    )
      .postData(
        ujson.write(
          ujson.Obj(
            "tag_name" -> releaseTag,
            "name" -> releaseTag
          )
        )
      )
      .header("Authorization", "token " + authKey)
      .asString
  }

  val exampleZips = Seq("example-1", "example-2", "example-3")
    .map { example =>
      os.copy(T.workspace / "example" / example, T.dest / example)
      os.copy(launcher().path, T.dest / example / "mill")
      os.proc("zip", "-r", T.dest / s"$example.zip", example).call(cwd = T.dest)
      (T.dest / s"$example.zip", label + "-" + example + ".zip")
    }

  val zips = exampleZips ++ Seq(
    (assembly().path, label + "-assembly"),
    (launcher().path, label)
  )

  for ((zip, name) <- zips) {
    upload.apply(
      zip,
      releaseTag,
      name,
      authKey,
      Settings.githubOrg,
      Settings.githubRepo
    )
  }
}

def validate(ev: Evaluator): Command[Unit] = T.command {
  T.task(MainModule.evaluateTasks(
    ev.withFailFast(false),
    Seq(
      "__.compile",
      "+",
      "__.mimaReportBinaryIssues",
      "+",
      "mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll",
      "__.sources",
      "+",
      "docs.antora.localPages"
    ),
    selectMode = SelectMode.Separated
  )(identity))()
  ()
}

object DependencyFetchDummy extends ScalaModule {
  override def scalaVersion = Deps.scalaVersion
  override def compileIvyDeps = Agg(
    Deps.semanticDbJava,
    Deps.semanticDB,
    Deps.asciidoctorj
  )
}
