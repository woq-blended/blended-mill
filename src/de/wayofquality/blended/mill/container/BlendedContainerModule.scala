package de.wayofquality.blended.mill.container

import mill._
import mill.scalalib._
import de.wayofquality.blended.mill.feature.BlendedFeatureModule
import de.wayofquality.blended.mill.modules.BlendedBaseModule
import de.wayofquality.blended.mill.utils.{ZipUtil, FilterUtil}
import mill.modules.Jvm
import os.{Path, RelPath}
import coursier.maven.MavenRepository
import mill.scalalib.publish.PublishInfo

/**
 * Define how blended containers are assembled.
 */
trait BlendedContainerModule extends BlendedBaseModule { ctModule =>

  def featureModuleDeps : Seq[BlendedFeatureModule] = Seq.empty
  def profileName : T[String] = artifactId()
  def profileVersion : T[String]
  def blendedCoreVersion : String 

  def debugTool : Boolean = false

  /**
   * The artifact map is a map from maven gavs to actual files. Typically all dependencies
   * that come from features are resolved by mill via coursier. In this list we keep track
   * of gav's to the absolute path of the associated file, so that we can pass this information
   * to blended's RuntimeConfigBuilder.
   */
  def artifactMap : T[Map[String, String]] = T {

    val bundles = T.traverse(featureModuleDeps)(fd =>
      T.task {

        fd.featureBundles().map{ fb =>
          val gav : String = fb.gav(scalaBinVersion())

          val singleDep : Seq[PathRef] = Lib.resolveDependencies(
            repositories,
            resolveCoursierDependency().apply(_),
            Agg(fb.dependency.exclude("*" -> "*")),
            false,
            mapDependencies = None,
            Some(implicitly[mill.util.Ctx.Log])
          ) match {
            case mill.api.Result.Success(r) =>
              if (r.iterator.isEmpty) {
                // TODO: This will appear in the log, but won't break the build. The RuntimeConfigBuilder
                // will try to download that file itself.
                // This happens i.e. when dependencies are not "jar"s
                T.log.error(s"No artifact found for [$gav]")
              }
              r.iterator.to(Seq)
            case mill.api.Result.Failure(m, _)  =>
              T.log.error(s"No artifact found for [$gav]")
              Seq.empty
              //sys.error(s"Failed to resolve [$gav] : $m")
            case _ => Seq.empty
          }

          singleDep.map(pr => (gav, pr.path.toIO.getAbsolutePath()))
        }
      }
    )()

    bundles.flatten.flatten.toMap
  }

  /**
   * The binaries packaged within the blended launcher. The content of this archive will be used as a starting
   * point for all blended containers.
   */
  def blendedLauncherZip : T [Agg[Dep]] = T { Agg(
    ivy"de.wayofquality.blended::blended.launcher:${blendedCoreVersion};classifier=dist".exclude("*" -> "*")
  )}

  /**
   * The dependency to the blended updater tools. These contain the tools to actually generate the
   * profile configuration.
   */
  def blendedToolsDeps : T [Agg[Dep]] = T { Agg(
    ivy"de.wayofquality.blended::blended.updater.tools:$blendedCoreVersion"
  )}

  /**
   * Resolve the launcher distribution file.
   */
  def resolveLauncher : T[PathRef] = T {
    val resolved = resolveDeps(blendedLauncherZip)()
    resolved.items.next()
  }

  /**
   * Unpack the content of the launcher distribution file.
   */
  def unpackLauncher : T[PathRef] = T {
    ZipUtil.unpackZip(resolveLauncher().path, T.dest)
    PathRef(T.dest)
  }

  /**
   * The resource path will be subject to filtering. We need to inject the profile name
   * and the profile version into the final profile.conf.
   */
  override def resources = T.sources { millSourcePath / "src"/ "profile" }

  /**
   * Run resource filtering across all <code>resources()</code>.
   */
  def filterResources : T[PathRef] = T {

    FilterUtil.filterDirs(
      unfilteredResourcesDirs = resources().map(_.path),
      pattern = """\$\{(.+?)\}""",
      filterTargetDir = T.dest,
      props = Map(
        "profile.name" -> profileName(),
        "profile.version" -> profileVersion(),
        "blended.version" -> blendedCoreVersion
      ),
      failOnMiss = false
    )

    PathRef(T.dest)
  }

  /**
   * Generate a profile conf that can be fed into RuntimeConfigBuilder. This will take the
   * filtered profile.conf from the sourcce files and append the configuration for the
   * container resource archive and the feature definitions.
   */
  def enhanceProfileConf : T[PathRef] = T {

    val content : String = os.read(filterResources().path / "profile.conf")

    val resources : String =
      s"""
         |resources = [
         |  { url="mvn:${ctResources.mvnGav((profileVersion()))}" }
         |]
         |""".stripMargin

    val features : Seq[String] = T.traverse(featureModuleDeps)(fd =>
      T.task { s"""  { name=${fd.artifactName()}, version="${fd.version()}" }""" }
    )()

    val generated = content + resources + features.mkString("features = [\n", ",\n", "\n]\n") + "bundles = []\n"

    os.write(T.dest / "profile.conf", generated)
    PathRef(T.dest / "profile.conf")
  }

  /**
   * Return a sequence of all feature files used in this container. These files will be handed over
   * to the RuntimeConfigBuilder.
   */
  def featureFiles : T[Seq[String]] = T.traverse(featureModuleDeps)(fd =>
    T.task { fd.featureConf() }
  )().map(_.path.toIO.getAbsolutePath)

  /**
   * The class we need to run to materialze the profile.
   */
  def runtimeConfigBuilderClass : String = "blended.updater.tools.configbuilder.RuntimeConfigBuilder"

  /**
   * Materialize the container profile.
   */
  def materializeProfile : T[PathRef] = T {

    val toolsCp : Agg[Path] = resolveDeps(blendedToolsDeps)().map(_.path)

    // First do required replacements in the source profile file
    filterResources().path / "profile" / "profile.conf"

    // This is the target profile file
    val profileDir : Path = T.dest

    // TODO: use other repo types ?
    val repoUrls : Seq[String] = repositories
      .filter(_.isInstanceOf[MavenRepository])
      .map(_.asInstanceOf[MavenRepository].root)

    // TODO: How do I determine if mill is started in debug mode
    val debugArgs : Seq[String] = if (debugTool) {
      Seq("--debug")
    } else {
      Seq.empty
    }

    // Assemble the command line parameters
    val toolArgs : Seq[String] = Seq(
      "-f", enhanceProfileConf().path.toIO.getAbsolutePath(),
      "-o", (profileDir / "profile.conf").toIO.getAbsolutePath(),
      "--create-launch-config", (profileDir / "launch.conf").toIO.getAbsolutePath(),
      "--download-missing",
      "--update-checksums",
      "--write-overlays-config",
      "--explode-resources",
      "--maven-artifact", ctResources.mvnGav(profileVersion()), ctResources.jar().path.toIO.getAbsolutePath()
    ) ++
      debugArgs ++
      featureFiles().flatMap(f => Seq[String]("--feature-repo", f)) ++
      artifactMap().flatMap{ case(k,v) => Seq[String]("--maven-artifact", k, v) } ++
      repoUrls.flatMap(r => Seq("--maven-url", r))

    T.log.debug(s"Calling $runtimeConfigBuilderClass with arguments : ${toolArgs.mkString("\n", "\n", "\n")}")

    Jvm.runSubprocess(
      mainClass = runtimeConfigBuilderClass,
      classPath = toolsCp,
      mainArgs = toolArgs
    )

    os.remove.all(profileDir / "META-INF")

    T.log.info(s"Materialized profile in [${profileDir.toIO.getAbsolutePath()}]")
    // Voila - the final profile configs
    PathRef(profileDir)
  }

  def containerExtraFiles  = T.sources { millSourcePath / "src"/ "package" / "container" }
  def profileExtraFiles = T.sources { millSourcePath / "src" / "package" / "profile" }

  /**
   * Create the runnable container by copying all resources into the right place.
   */
  def container : T [PathRef] = T {

    /**
     * Helper to copy the content of a given directory into a destination directory.
     * The given directory may not be present (i.e. no extra files are required).
     * Files within the destination folder will be overwritten by the content of the
     * given directory.
     */
    def copyOver(src: Path, dest: Path) : Unit = {
      if (src.toIO.exists()) {
        os.walk(src).foreach { p =>
          if (p.toIO.isFile()) {
            os.copy(p, dest / p.relativeTo(src), replaceExisting = true, createFolders = true)
          }
        }
      }
    }

    val ctDir = T.dest

    val launcher : Path = unpackLauncher().path
    val profile : Path = materializeProfile().path

    val profileDir = ctDir / "profiles" / profileName() / profileVersion()

    os.list(launcher).iterator.foreach { p => os.copy.into(p, ctDir) }
    os.remove.all(ctDir / "META-INF")

    os.copy.into(profile / "launch.conf", ctDir)
    os.copy(profile, profileDir, createFolders = true)

    containerExtraFiles().map(_.path).foreach(copyOver(_, ctDir) )
    profileExtraFiles().map(_.path).foreach(copyOver(_, profileDir))

    os.remove(ctDir / "profiles" / profileName() / profileVersion() / "launch.conf")

    PathRef(ctDir)
  }

  /**
   * Package the runnable container into a zip archive.
   */
  def dist = T {
    val zip = T.dest / "container.zip"
    ZipUtil.createZip(
      outputPath = zip,
      inputPaths = Seq(container().path),
      prefix = s"${artifactId()}-${profileVersion()}/"
    )

    PathRef(zip)
  }

  /**
   * Package a deployment package that can be uploaded to a running blended container as a self contained profile
   */
  def deploymentpack = T {

    val deploy = T.dest / "deployment.zip"

    val profileDir : Path = container().path / "profiles" / profileName() / profileVersion()

    val includes : Seq[RelPath] = Seq( "profile.conf", "bundles", "resources").map(s => RelPath(s))

    ZipUtil.createZip(
      outputPath = deploy,
      inputPaths = Seq(profileDir),
      fileFilter = (_, rel) => includes.exists(i => rel.startsWith(i)),
      includeDirs = true
    )

    PathRef(deploy)
  }

  /**
   * Make sure the container zips are also published.
   */
  def ctArtifacts : T[Seq[PublishInfo]]= T  { Seq(
    PublishInfo(file = dist(), classifier = Some("full-nojre"), ext = "zip", ivyConfig = "compile", ivyType = "dist"),
    PublishInfo(file = deploymentpack(), classifier = Some("deploymentpack"), ext = "zip", ivyConfig = "compile", ivyType = "dist")
  )}

  // TODO: Apply magic to turn ctResources to magic overridable val (i.e. as in ScoverageData)
  // per default package downloadable resources in a separate jar
  object ctResources extends BlendedBaseModule { base =>

    override def baseDir = ctModule.baseDir 
    override def scalaVersion : T[String] = T { ctModule.scalaVersion() }
    type ProjectDeps = ctModule.ProjectDeps
    override def deps = ctModule.deps

    override def artifactName : T[String] = T { ctModule.artifactName() + ".resources" }

    def mvnGav : String => String = version => 
      s"${deps.blendedOrg}:${blendedModule}:${version}"
  }

  /**
   * Docker definitions if we want to create a docker image from the container.
   */
  trait Docker extends Module {

    /**
     * The maintainer as it should appear in the docker file.
     */
    def maintainer : String = "Blended Team"

    /**
     * The base image that shall be used for the generated docker image.
     */
    def baseImage : String = "atooni/zulu-8-alpine:1.0.1"

    /**
     * The ports exposed from the docker image
     */
    def exposedPorts : Seq[Int] = Seq.empty

    /**
     * The folder under /opt where the application shall be installed.
     */
    def appFolder : T[String] = T { ctModule.profileName() }

    /**
     * The user who owns the application folder
     */
    def appUser : String = "blended"

    def dockerImage : T[String]

    def dockerconfig : T[PathRef] = T {

      val dir = T.dest

      os.copy(ctModule.container().path, dir / "files" / "container" / appFolder(), createFolders = true)

      val content : String =
        s"""FROM $baseImage
           |LABEL maintainer="$maintainer"
           |LABEL version="${ctModule.profileVersion()}"
           |ADD files/container /opt
           |RUN chown -R $appUser.$appUser /opt/${appFolder()}
           |USER $appUser
           |ENV JAVA_HOME /opt/java
           |ENV PATH $${PATH}:$${JAVA_HOME}/bin
           |ENTRYPOINT ["/bin/sh", "/opt/${appFolder()}/bin/blended.sh"]
           |""".stripMargin ++ exposedPorts.map(p => s"EXPOSE $p").mkString("\n", "\n", "\n")

      os.write(dir / "Dockerfile", content)

      PathRef(dir)
    }

    def dockerbuild()  = T.command {
      val process = Jvm.spawnSubprocess(commandArgs = Seq(
        "docker", "build", "-t", dockerImage(), "."
      ), envArgs = Map.empty, workingDir = dockerconfig().path)

      process.join()
      process.exitCode()
    }
  }
}
