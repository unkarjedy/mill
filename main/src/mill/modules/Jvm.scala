package mill.modules

import coursier.cache.ArtifactError
import coursier.util.{Gather, Task}
import coursier.{Dependency, Repository, Resolution}
import mill.BuildInfo
import mill.api.Loose.Agg
import mill.api._
import mill.main.client.InputPumper
import mill.modules.Assembly.{AppendEntry, WriteOnceEntry}
import os.SubProcess
import upickle.default.{ReadWriter => RW}

import java.io._
import java.lang.reflect.Modifier
import java.net.URI
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{FileSystems, Files, NoSuchFileException, StandardOpenOption}
import java.util.Collections
import java.util.jar.{Attributes, JarFile, Manifest}
import scala.annotation.tailrec
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.Properties.isWin
import scala.util.{Failure, Success, Try, Using}

object Jvm {

  private val CoursierRetryCount = 5
  private val CoursierRetryWait = 100

  /**
   * Runs a JVM subprocess with the given configuration and returns a
   * [[os.CommandResult]] with it's aggregated output and error streams
   */
  def callSubprocess(
      mainClass: String,
      classPath: Agg[os.Path],
      jvmArgs: Seq[String] = Seq.empty,
      envArgs: Map[String, String] = Map.empty,
      mainArgs: Seq[String] = Seq.empty,
      workingDir: os.Path = null,
      streamOut: Boolean = true
  )(implicit ctx: Ctx) = {

    val commandArgs =
      Vector(javaExe) ++
        jvmArgs ++
        Vector("-cp", classPath.iterator.mkString(java.io.File.pathSeparator), mainClass) ++
        mainArgs

    val workingDir1 = Option(workingDir).getOrElse(ctx.dest)
    os.makeDir.all(workingDir1)

    os.proc(commandArgs).call(cwd = workingDir1, env = envArgs)
  }

  /**
   * Resolves a tool to a path under the currently used JDK (if known).
   */
  def jdkTool(toolName: String): String = {
    sys.props
      .get("java.home")
      .map(h =>
        if (isWin) new File(h, s"bin\\${toolName}.exe")
        else new File(h, s"bin/${toolName}")
      )
      .filter(f => f.exists())
      .fold(toolName)(_.getAbsolutePath())

  }

  def javaExe: String = jdkTool("java")

  /**
   * Runs a JVM subprocess with the given configuration and streams
   * it's stdout and stderr to the console.
   * @param mainClass The main class to run
   * @param classPath The classpath
   * @param JvmArgs Arguments given to the forked JVM
   * @param envArgs Environment variables used when starting the forked JVM
   * @param workingDir The working directory to be used by the forked JVM
   * @param background `true` if the forked JVM should be spawned in background
   * @param useCpPassingJar When `false`, the `-cp` parameter is used to pass the classpath
   *                        to the forked JVM.
   *                        When `true`, a temporary empty JAR is created
   *                        which contains a `Class-Path` manifest entry containing the actual classpath.
   *                        This might help with long classpaths on OS'es (like Windows)
   *                        which only supports limited command-line length
   */
  def runSubprocess(
      mainClass: String,
      classPath: Agg[os.Path],
      jvmArgs: Seq[String] = Seq.empty,
      envArgs: Map[String, String] = Map.empty,
      mainArgs: Seq[String] = Seq.empty,
      workingDir: os.Path = null,
      background: Boolean = false,
      useCpPassingJar: Boolean = false
  )(implicit ctx: Ctx): Unit = {

    val cp =
      if (useCpPassingJar && !classPath.iterator.isEmpty) {
        val passingJar = os.temp(prefix = "run-", suffix = ".jar", deleteOnExit = false)
        ctx.log.debug(
          s"Creating classpath passing jar '${passingJar}' with Class-Path: ${classPath.iterator.map(
              _.toNIO.toUri().toURL().toExternalForm()
            ).mkString(" ")}"
        )
        createClasspathPassingJar(passingJar, classPath)
        Agg(passingJar)
      } else {
        classPath
      }

    val args =
      Vector(javaExe) ++
        jvmArgs ++
        Vector("-cp", cp.iterator.mkString(java.io.File.pathSeparator), mainClass) ++
        mainArgs

    ctx.log.debug(s"Run subprocess with args: ${args.map(a => s"'${a}'").mkString(" ")}")

    if (background) spawnSubprocess(args, envArgs, workingDir)
    else runSubprocess(args, envArgs, workingDir)
  }

  /**
   * Runs a generic subprocess and waits for it to terminate.
   */
  def runSubprocess(commandArgs: Seq[String], envArgs: Map[String, String], workingDir: os.Path) = {
    val process = spawnSubprocess(commandArgs, envArgs, workingDir)
    val shutdownHook = new Thread("subprocess-shutdown") {
      override def run(): Unit = {
        System.err.println("Host JVM shutdown. Forcefully destroying subprocess ...")
        process.destroy()
      }
    }
    Runtime.getRuntime().addShutdownHook(shutdownHook)
    try {
      process.waitFor()
    } catch {
      case e: InterruptedException =>
        System.err.println("Interrupted. Forcefully destroying subprocess ...")
        process.destroy()
        // rethrow
        throw e
    } finally {
      Runtime.getRuntime().removeShutdownHook(shutdownHook)
    }
    if (process.exitCode() == 0) ()
    else throw new Exception("Interactive Subprocess Failed (exit code " + process.exitCode() + ")")
  }

  /**
   * Spawns a generic subprocess, streaming the stdout and stderr to the
   * console. If the System.out/System.err have been substituted, makes sure
   * that the subprocess's stdout and stderr streams go to the subtituted
   * streams
   */
  def spawnSubprocess(
      commandArgs: Seq[String],
      envArgs: Map[String, String],
      workingDir: os.Path
  ): SubProcess = {
    // If System.in is fake, then we pump output manually rather than relying
    // on `os.Inherit`. That is because `os.Inherit` does not follow changes
    // to System.in/System.out/System.err, so the subprocess's streams get sent
    // to the parent process's origin outputs even if we want to direct them
    // elsewhere
    if (System.in.isInstanceOf[PipedInputStream]) {
      val process = os.proc(commandArgs).spawn(
        cwd = workingDir,
        env = envArgs,
        stdin = os.Pipe,
        stdout = os.Pipe,
        stderr = os.Pipe
      )

      val sources = Seq(
        (process.stdout, System.out, "spawnSubprocess.stdout", false, () => true),
        (process.stderr, System.err, "spawnSubprocess.stderr", false, () => true),
        (System.in, process.stdin, "spawnSubprocess.stdin", true, () => process.isAlive())
      )

      for ((std, dest, name, checkAvailable, runningCheck) <- sources) {
        val t = new Thread(
          new InputPumper(std, dest, checkAvailable, () => runningCheck()),
          name
        )
        t.setDaemon(true)
        t.start()
      }

      process
    } else {
      os.proc(commandArgs).spawn(
        cwd = workingDir,
        env = envArgs,
        stdin = os.Inherit,
        stdout = os.Inherit,
        stderr = os.Inherit
      )
    }
  }

  def runLocal(
      mainClass: String,
      classPath: Agg[os.Path],
      mainArgs: Seq[String] = Seq.empty
  )(implicit ctx: Ctx): Unit = {
    inprocess(
      classPath,
      classLoaderOverrideSbtTesting = false,
      isolated = true,
      closeContextClassLoaderWhenDone = true,
      cl => {
        getMainMethod(mainClass, cl).invoke(null, mainArgs.toArray)
      }
    )
  }

  private def getMainMethod(mainClassName: String, cl: ClassLoader) = {
    val mainClass = cl.loadClass(mainClassName)
    val method = mainClass.getMethod("main", classOf[Array[String]])
    // jvm allows the actual main class to be non-public and to run a method in the non-public class,
    //  we need to make it accessible
    method.setAccessible(true)
    val modifiers = method.getModifiers
    if (!Modifier.isPublic(modifiers))
      throw new NoSuchMethodException(mainClassName + ".main is not public")
    if (!Modifier.isStatic(modifiers))
      throw new NoSuchMethodException(mainClassName + ".main is not static")
    method
  }

  def inprocess[T](
      classPath: Agg[os.Path],
      classLoaderOverrideSbtTesting: Boolean,
      isolated: Boolean,
      closeContextClassLoaderWhenDone: Boolean,
      body: ClassLoader => T
  )(implicit ctx: Ctx.Home): T = {
    mill.util.Jvm.inprocess(
      classPath,
      classLoaderOverrideSbtTesting,
      isolated,
      closeContextClassLoaderWhenDone,
      body
    )
  }

  def createManifest(mainClass: Option[String]): JarManifest = {
    val main =
      Map[String, String](
        java.util.jar.Attributes.Name.MANIFEST_VERSION.toString -> "1.0",
        "Created-By" -> s"Mill ${BuildInfo.millVersion}",
        "Tool" -> s"Mill-${BuildInfo.millVersion}"
      ) ++
        mainClass.map(mc => Map(java.util.jar.Attributes.Name.MAIN_CLASS.toString -> mc)).getOrElse(
          Map.empty
        )
    JarManifest(main)
  }

  /**
   * Create a jar file containing all files from the specified input Paths,
   * called out.jar in the implicit ctx.dest folder. An optional main class may
   * be provided for the jar. An optional filter function may also be provided to
   * selectively include/exclude specific files.
   * @param inputPaths - `Agg` of `os.Path`s containing files to be included in the jar
   * @param fileFilter - optional file filter to select files to be included.
   *                   Given a `os.Path` (from inputPaths) and a `os.RelPath` for the individual file,
   *                   return true if the file is to be included in the jar.
   * @param ctx - implicit `Ctx.Dest` used to determine the output directory for the jar.
   * @return - a `PathRef` for the created jar.
   */
  def createJar(
      inputPaths: Agg[os.Path],
      manifest: JarManifest = JarManifest.Default,
      fileFilter: (os.Path, os.RelPath) => Boolean = (_, _) => true
  )(implicit ctx: Ctx.Dest): PathRef = {
    val outputPath = ctx.dest / "out.jar"
    createJar(
      jar = outputPath,
      inputPaths = inputPaths,
      manifest = manifest,
      fileFilter = fileFilter
    )
    PathRef(outputPath)
  }

  def createJar(
      jar: os.Path,
      inputPaths: Agg[os.Path],
      manifest: JarManifest,
      fileFilter: (os.Path, os.RelPath) => Boolean
  ): Unit =
    JarOps.jar(jar, inputPaths, manifest.build, fileFilter, includeDirs = true, timestamp = None)

  def createClasspathPassingJar(jar: os.Path, classpath: Agg[os.Path]): Unit = {
    createJar(
      jar = jar,
      inputPaths = Agg(),
      manifest = JarManifest.Default.add(
        "Class-Path" -> classpath.iterator.map(_.toNIO.toUri().toURL().toExternalForm()).mkString(
          " "
        )
      ),
      fileFilter = (_, _) => true
    )
  }

  def createAssembly(
      inputPaths: Agg[os.Path],
      manifest: JarManifest = JarManifest.Default,
      prependShellScript: String = "",
      base: Option[os.Path] = None,
      assemblyRules: Seq[Assembly.Rule] = Assembly.defaultRules
  )(implicit ctx: Ctx.Dest with Ctx.Log): PathRef = {
    val tmp = ctx.dest / "out-tmp.jar"

    val baseUri = "jar:" + tmp.toIO.getCanonicalFile.toURI.toASCIIString
    val hm = base.fold(Map("create" -> "true")) { b =>
      os.copy(b, tmp)
      Map.empty
    }
    Using.resource(FileSystems.newFileSystem(URI.create(baseUri), hm.asJava)) { zipFs =>
      val manifestPath = zipFs.getPath(JarFile.MANIFEST_NAME)
      Files.createDirectories(manifestPath.getParent)
      val manifestOut = Files.newOutputStream(
        manifestPath,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.CREATE
      )
      manifest.build.write(manifestOut)
      manifestOut.close()

      val (mappings, resourceCleaner) = Assembly.loadShadedClasspath(inputPaths, assemblyRules)
      try {
        Assembly.groupAssemblyEntries(mappings, assemblyRules).foreach {
          case (mapping, entry) =>
            val path = zipFs.getPath(mapping).toAbsolutePath
            entry match {
              case entry: AppendEntry =>
                val separated = entry.inputStreams
                  .flatMap(inputStream =>
                    Seq(new ByteArrayInputStream(entry.separator.getBytes), inputStream())
                  )
                val cleaned = if (Files.exists(path)) separated else separated.drop(1)
                val concatenated =
                  new SequenceInputStream(Collections.enumeration(cleaned.asJava))
                writeEntry(path, concatenated, append = true)
              case entry: WriteOnceEntry => writeEntry(path, entry.inputStream(), append = false)
            }
        }
      } finally {
        resourceCleaner()
      }
    }

    val output = ctx.dest / "out.jar"
    // Prepend shell script and make it executable
    if (prependShellScript.isEmpty) os.move(tmp, output)
    else {
      val lineSep = if (!prependShellScript.endsWith("\n")) "\n\r\n" else ""
      os.write(output, prependShellScript + lineSep)
      os.write.append(output, os.read.inputStream(tmp))

      if (!scala.util.Properties.isWin) {
        os.perms.set(
          output,
          os.perms(output)
            + PosixFilePermission.GROUP_EXECUTE
            + PosixFilePermission.OWNER_EXECUTE
            + PosixFilePermission.OTHERS_EXECUTE
        )
      }
    }

    PathRef(output)
  }

  private def writeEntry(p: java.nio.file.Path, inputStream: InputStream, append: Boolean): Unit = {
    if (p.getParent != null) Files.createDirectories(p.getParent)
    val options =
      if (append) Seq(StandardOpenOption.APPEND, StandardOpenOption.CREATE)
      else Seq(StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)

    val outputStream = java.nio.file.Files.newOutputStream(p, options: _*)
    IO.stream(inputStream, outputStream)
    outputStream.close()
    inputStream.close()
  }

  def universalScript(
      shellCommands: String,
      cmdCommands: String,
      shebang: Boolean = false
  ): String = {
    Seq(
      if (shebang) "#!/usr/bin/env sh" else "",
      "@ 2>/dev/null # 2>nul & echo off & goto BOF\r",
      ":",
      shellCommands.replaceAll("\r\n|\n", "\n"),
      "exit",
      Seq(
        "",
        ":BOF",
        "setlocal",
        "@echo off",
        cmdCommands.replaceAll("\r\n|\n", "\r\n"),
        "endlocal",
        "exit /B %errorlevel%",
        ""
      ).mkString("\r\n")
    ).filterNot(_.isEmpty).mkString("\n")
  }

  def launcherUniversalScript(
      mainClass: String,
      shellClassPath: Agg[String],
      cmdClassPath: Agg[String],
      jvmArgs: Seq[String],
      shebang: Boolean = false
  ): String = {
    universalScript(
      shellCommands =
        s"""exec java ${jvmArgs.mkString(" ")} $$JAVA_OPTS -cp "${shellClassPath.iterator.mkString(
            ":"
          )}" '$mainClass' "$$@"""",
      cmdCommands =
        s"""java ${jvmArgs.mkString(" ")} %JAVA_OPTS% -cp "${cmdClassPath.iterator.mkString(
            ";"
          )}" $mainClass %*""",
      shebang = shebang
    )
  }
  def createLauncher(mainClass: String, classPath: Agg[os.Path], jvmArgs: Seq[String])(implicit
      ctx: Ctx.Dest
  ): PathRef = {
    val isWin = scala.util.Properties.isWin
    val isBatch =
      isWin && !(org.jline.utils.OSUtils.IS_CYGWIN || org.jline.utils.OSUtils.IS_MSYSTEM)
    val outputPath = ctx.dest / (if (isBatch) "run.bat" else "run")
    val classPathStrs = classPath.map(_.toString)

    os.write(outputPath, launcherUniversalScript(mainClass, classPathStrs, classPathStrs, jvmArgs))

    if (!isWin) {
      val perms = Files.getPosixFilePermissions(outputPath.toNIO)
      perms.add(PosixFilePermission.GROUP_EXECUTE)
      perms.add(PosixFilePermission.OWNER_EXECUTE)
      perms.add(PosixFilePermission.OTHERS_EXECUTE)
      Files.setPosixFilePermissions(outputPath.toNIO, perms)
    }
    PathRef(outputPath)
  }

  /**
   * Somewhat generic way to retry some action and a Workaround for https://github.com/com-lihaoyi/mill/issues/1028
   *
   * Specifically build for coursier API interactions, which is known to have some concurrency issues which we handle on a known case basis.
   *
   * @param retryCount The max retry count
   * @param ctx The context to use ot show log messages (if defined)
   * @param errorMsgExtractor A generic way to get the error message of a run of `f`
   * @param f The actual operation to retry, if it results in a known concurrency error
   * @tparam T The result type of the computation
   * @return The result of the computation. If the computation was retries and finally succeeded, proviously occured errors will not be included in the result.
   */
  @tailrec
  private def retry[T](
      retryCount: Int = CoursierRetryCount,
      ctx: Option[Ctx.Log],
      errorMsgExtractor: T => Seq[String]
  )(f: () => T): T = {
    val tried = Try(f())
    tried match {
      case Failure(e: NoSuchFileException)
          if retryCount > 0 && e.getMessage.contains("__sha1.computed") =>
        // this one is not detected by coursier itself, so we try-catch handle it
        // I assume, this happens when another coursier thread already moved or rename dthe temporary file
        ctx.foreach(_.log.debug(
          s"Detected a concurrent download issue in coursier. Attempting a retry (${retryCount} left)"
        ))
        Thread.sleep(CoursierRetryWait)
        retry(retryCount - 1, ctx, errorMsgExtractor)(f)
      case Success(res) if retryCount > 0 =>
        val errors = errorMsgExtractor(res)
        if (errors.exists(e => e.contains("concurrent download"))) {
          ctx.foreach(_.log.debug(
            s"Detected a concurrent download issue in coursier. Attempting a retry (${retryCount} left)"
          ))
          Thread.sleep(CoursierRetryWait)
          retry(retryCount - 1, ctx, errorMsgExtractor)(f)
        } else if (errors.exists(e => e.contains("checksum not found"))) {
          ctx.foreach(_.log.debug(
            s"Detected a checksum download issue in coursier. Attempting a retry (${retryCount} left)"
          ))
          Thread.sleep(CoursierRetryWait)
          retry(retryCount - 1, ctx, errorMsgExtractor)(f)
        } else res
      case r => r.get
    }
  }

  /**
   * Resolve dependencies using Coursier.
   *
   * We do not bother breaking this out into the separate ZincWorkerApi classpath,
   * because Coursier is already bundled with mill/Ammonite to support the
   * `import $ivy` syntax.
   */
  def resolveDependencies(
      repositories: Seq[Repository],
      deps: IterableOnce[coursier.Dependency],
      force: IterableOnce[coursier.Dependency],
      sources: Boolean = false,
      mapDependencies: Option[Dependency => Dependency] = None,
      customizer: Option[coursier.core.Resolution => coursier.core.Resolution] = None,
      ctx: Option[mill.api.Ctx.Log] = None,
      coursierCacheCustomizer: Option[
        coursier.cache.FileCache[Task] => coursier.cache.FileCache[Task]
      ] = None
  ): Result[Agg[PathRef]] = {

    val (_, resolution) = resolveDependenciesMetadata(
      repositories,
      deps,
      force,
      mapDependencies,
      customizer,
      ctx,
      coursierCacheCustomizer
    )
    val errs = resolution.errors

    if (errs.nonEmpty) {
      val header =
        s"""|
            |Resolution failed for ${errs.length} modules:
            |--------------------------------------------
            |""".stripMargin

      val helpMessage =
        s"""|
            |--------------------------------------------
            |
            |For additional information on library dependencies, see the docs at
            |${mill.BuildInfo.millDocUrl}/mill/Library_Dependencies.html""".stripMargin

      val errLines = errs.map {
        case ((module, vsn), errMsgs) => s"  ${module.trim}:$vsn \n\t" + errMsgs.mkString("\n\t")
      }.mkString("\n")
      val msg = header + errLines + "\n" + helpMessage + "\n"
      Result.Failure(msg)
    } else {

      val coursierCache0 = coursier.cache.FileCache[Task]().noCredentials
      val coursierCache = coursierCacheCustomizer.getOrElse(
        identity[coursier.cache.FileCache[Task]](_)
      ).apply(coursierCache0)

      def load(artifacts: Seq[coursier.util.Artifact]): (Seq[ArtifactError], Seq[File]) = {
        import scala.concurrent.ExecutionContext.Implicits.global
        val loadedArtifacts = Gather[Task].gather(
          for (a <- artifacts)
            yield coursierCache.file(a).run.map(a.optional -> _)
        ).unsafeRun()

        val errors = loadedArtifacts.collect {
          case (false, Left(x)) => x
          case (true, Left(x)) if !x.notFound => x
        }
        val successes = loadedArtifacts.collect { case (_, Right(x)) => x }

        (errors, successes)
      }

      val sourceOrJar =
        if (sources) {
          resolution.artifacts(
            types = Set(coursier.Type.source, coursier.Type.javaSource),
            classifiers = Some(Seq(coursier.Classifier("sources")))
          )
        } else resolution.artifacts(
          types = Set(
            coursier.Type.jar,
            coursier.Type.testJar,
            coursier.Type.bundle,
            coursier.Type("orbit"),
            coursier.Type("eclipse-plugin"),
            coursier.Type("maven-plugin")
          )
        )

      val (errors, successes) = retry(
        ctx = ctx,
        errorMsgExtractor = (res: (Seq[ArtifactError], Seq[File])) => res._1.map(_.describe)
      ) {
        () => load(sourceOrJar)
      }

      if (errors.isEmpty) {
        mill.Agg.from(
          successes.map(p => PathRef(os.Path(p), quick = true)).filter(_.path.ext == "jar")
        )
      } else {
        val errorDetails = errors.map(e => s"${System.lineSeparator()}  ${e.describe}").mkString
        Result.Failure(
          s"Failed to load ${if (sources) "source " else ""}dependencies" + errorDetails
        )
      }
    }
  }

  def resolveDependenciesMetadata(
      repositories: Seq[Repository],
      deps: IterableOnce[coursier.Dependency],
      force: IterableOnce[coursier.Dependency],
      mapDependencies: Option[Dependency => Dependency] = None,
      customizer: Option[coursier.core.Resolution => coursier.core.Resolution] = None,
      ctx: Option[mill.api.Ctx.Log] = None,
      coursierCacheCustomizer: Option[
        coursier.cache.FileCache[Task] => coursier.cache.FileCache[Task]
      ] = None
  ): (Seq[Dependency], Resolution) = {

    val cachePolicies = coursier.cache.CacheDefaults.cachePolicies

    val forceVersions = force.iterator
      .map(mapDependencies.getOrElse(identity[Dependency](_)))
      .map { d => d.module -> d.version }
      .toMap

    val start0 = Resolution()
      .withRootDependencies(
        deps.iterator.map(mapDependencies.getOrElse(identity[Dependency](_))).toSeq
      )
      .withForceVersions(forceVersions)
      .withMapDependencies(mapDependencies)

    val start = customizer.getOrElse(identity[Resolution](_)).apply(start0)

    val resolutionLogger = ctx.map(c => new TickerResolutionLogger(c))
    val coursierCache0 = resolutionLogger match {
      case None => coursier.cache.FileCache[Task]().withCachePolicies(cachePolicies)
      case Some(l) =>
        coursier.cache.FileCache[Task]()
          .withCachePolicies(cachePolicies)
          .withLogger(l)
    }
    val coursierCache = coursierCacheCustomizer.getOrElse(
      identity[coursier.cache.FileCache[Task]](_)
    ).apply(coursierCache0)

    val fetches = coursierCache.fetchs

    val fetch = coursier.core.ResolutionProcess.fetch(repositories, fetches.head, fetches.tail)

    import scala.concurrent.ExecutionContext.Implicits.global

    val resolution =
      retry(ctx = ctx, errorMsgExtractor = (r: Resolution) => r.errors.flatMap(_._2)) {
        () => start.process.run(fetch).unsafeRun()
      }

    (deps.iterator.to(Seq), resolution)
  }

  /**
   * A Coursier Cache.Logger implementation that updates the ticker with the count and
   * overall byte size of artifacts being downloaded.
   *
   * In practice, this ticker output gets prefixed with the current target for which
   * dependencies are being resolved, using a [[mill.util.ProxyLogger]] subclass.
   */
  class TickerResolutionLogger(ctx: Ctx.Log) extends coursier.cache.CacheLogger {
    case class DownloadState(var current: Long, var total: Long)
    var downloads = new mutable.TreeMap[String, DownloadState]()
    var totalDownloadCount = 0
    var finishedCount = 0
    var finishedState = DownloadState(0, 0)

    def updateTicker(): Unit = {
      val sums = downloads.values
        .fold(DownloadState(0, 0)) {
          (s1, s2) =>
            DownloadState(
              s1.current + s2.current,
              Math.max(s1.current, s1.total) + Math.max(s2.current, s2.total)
            )
        }
      sums.current += finishedState.current
      sums.total += finishedState.total
      ctx.log.ticker(
        s"Downloading [${downloads.size + finishedCount}/$totalDownloadCount] artifacts (~${sums.current}/${sums.total} bytes)"
      )
    }

    override def downloadingArtifact(url: String): Unit = synchronized {
      totalDownloadCount += 1
      downloads += url -> DownloadState(0, 0)
      updateTicker()
    }

    override def downloadLength(
        url: String,
        totalLength: Long,
        alreadyDownloaded: Long,
        watching: Boolean
    ): Unit = synchronized {
      val state = downloads(url)
      state.current = alreadyDownloaded
      state.total = totalLength
      updateTicker()
    }

    override def downloadProgress(url: String, downloaded: Long): Unit = synchronized {
      val state = downloads(url)
      state.current = downloaded
      updateTicker()
    }

    override def downloadedArtifact(url: String, success: Boolean): Unit = synchronized {
      val state = downloads(url)
      finishedState.current += state.current
      finishedState.total += Math.max(state.current, state.total)
      finishedCount += 1
      downloads -= url
      updateTicker()
    }
  }

  object JarManifest {
    implicit val jarManifestRW: RW[JarManifest] = upickle.default.macroRW
    final val Default = createManifest(None)
  }

  /**
   * Represents a JAR manifest.
   * @param main the main manifest attributes
   * @param groups additional attributes for named entries
   */
  final case class JarManifest(
      main: Map[String, String] = Map.empty,
      groups: Map[String, Map[String, String]] = Map.empty
  ) {
    def add(entries: (String, String)*): JarManifest = copy(main = main ++ entries)
    def addGroup(group: String, entries: (String, String)*): JarManifest =
      copy(groups = groups + (group -> (groups.getOrElse(group, Map.empty) ++ entries)))

    /** Constructs a [[java.util.jar.Manifest]] from this JarManifest. */
    def build: Manifest = {
      val manifest = new Manifest
      val mainAttributes = manifest.getMainAttributes
      main.foreach { case (key, value) => mainAttributes.putValue(key, value) }
      val entries = manifest.getEntries
      for ((group, attribs) <- groups) {
        val attrib = new Attributes
        attribs.foreach { case (key, value) => attrib.putValue(key, value) }
        entries.put(group, attrib)
      }
      manifest
    }
  }

}
