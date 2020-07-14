package de.wayofquality.blended.mill.container

import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions, ConfigRenderOptions}
import coursier.MavenRepository
import de.wayofquality.blended.mill.feature.{Feature, FeatureBundle, GAVHelper}
import mill._
import mill.scalalib._
import de.wayofquality.blended.mill.modules.BlendedBaseModule
import de.wayofquality.blended.mill.utils.config.CopyHelper
import de.wayofquality.blended.mill.utils.{FilterUtil, ZipUtil}
import mill.define.{Command, Sources}
import mill.modules.Jvm
import os.{Path, RelPath}

import scala.util.Try
import mill.scalalib.publish.PublishInfo
import de.wayofquality.blended.mill.publish.BlendedPublishModule
import de.wayofquality.blended.mill.feature.FeatureRef

/**
 * Define how blended containers are assembled.
 */

trait BlendedContainerModule extends BlendedBaseModule with BlendedPublishModule { ctModule =>

  def features : T[Seq[FeatureRef]] = T { Seq.empty[FeatureRef] }
  def profileName : T[String] = artifactId()
  def profileVersion : T[String]
  def blendedCoreVersion : String

  def debugTool : Boolean = false

  /**
   * The artifact map is a map from maven gavs to actual files. Typically all dependencies
   * that come from features are resolved by mill via coursier. In this list we keep track
   * of gav's to the absolute path of the associated file, so that we can pass this information
   * to blended's ProfileBuilder.
   */
  def artifactMap : T[Map[String, String]] = T {

    def refKeys(f : FeatureRef) : List[String] =
      f.names.map(n => s"mvn:${GAVHelper.gav(scalaBinVersion())(f.dependency)}#$n").toList

    def repoKey(repo : Feature) : String = s"${repo.repoUrl}#${repo.name}"

    val featureConfigs : Map[String, Feature] = featureFiles().map { pr =>
      val cfgString = os.read(pr.path)
      T.log.ticker(s"Reading feature config [${pr.path}] ")
      val cfg : Config = ConfigFactory.parseString(cfgString, ConfigParseOptions.defaults())

      Feature.fromConfig(scalaBinVersion())(cfg).get
    }.map(f => repoKey(f) -> f).toMap

    /** Main method to walk the feature references of a container and come up with a list
     *  of all referenced bundles.
     */
    def resolveFeatures(
      seenFeatureRefs : List[String],
      pendingFeatureRefs : List[String],
      bundles : List[FeatureBundle]
    ) : Try[List[FeatureBundle]] = Try {
      pendingFeatureRefs match {
        // No more pending features => we have found all bundles
        case Nil => bundles
        case head :: tail =>
          T.log.ticker(s"Reading bundles for feature ref [$head]")

          val feature : Feature = featureConfigs.get(head) match {
            case None => throw new Exception(s"Feature Config for [$head] has not been resolved.")
            case Some(f) => f
          }

          val newPending : List[String] =
            (feature.features.flatMap(fr => refKeys(fr)) ++ tail).distinct.toList

          val newBundles : List[FeatureBundle] = (feature.bundles ++ bundles).distinct.toList

          resolveFeatures(head :: seenFeatureRefs, newPending, newBundles).get
      }
    }

    val bundles : List[FeatureBundle] = resolveFeatures(
      seenFeatureRefs = List.empty,
      pendingFeatureRefs = features().flatMap { f => refKeys(f) }.toList,
      bundles = List.empty
    ).get

    val resolvedBundles : Seq[Seq[(String, String)]] = bundles.map{ bundle =>
      val gav : String = GAVHelper.gav(scalaBinVersion())(bundle.dependency)

      T.log.ticker(s"Resolving bundle [$bundle]")

      val singleDep : Seq[PathRef] = Lib.resolveDependencies(
        repositories,
        resolveCoursierDependency().apply(_),
        Agg(bundle.dependency.exclude("*" -> "*")),
        false,
        mapDependencies = None,
        Some(implicitly[mill.util.Ctx.Log])
      ) match {
        case mill.api.Result.Success(r) =>
          if (r.iterator.isEmpty) {
            // TODO: This will appear in the log, but won't break the build. The ProfileBuilder
            // will try to download that file itself.
            // This happens i.e. when dependencies are not "jar"s
            T.log.error(s"No artifact found for [${bundle.dependency}]")
          }
          r.iterator.to(Seq)
        case mill.api.Result.Failure(m, _)  =>
          T.log.error(s"Failed to resolve artifact [${bundle.dependency}]")
          Seq.empty
          //sys.error(s"Failed to resolve [$gav] : $m")
        case _ => Seq.empty
      }

      singleDep.map(pr => (gav, pr.path.toIO.getAbsolutePath()))
    }

    resolvedBundles.flatten.toMap
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
    ivy"de.wayofquality.blended::blended.updater.tools:$blendedCoreVersion",
    ivy"ch.qos.logback:logback-core:1.2.3",
    ivy"ch.qos.logback:logback-classic:1.2.3",
    ivy"org.slf4j:slf4j-api:1.7.25"
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
   * Generate a profile conf that can be fed into ProfileBuilder. This will take the
   * filtered profile.conf from the sourcce files and append the configuration for the
   * container resource archive and the feature definitions.
   */
  def enhanceProfileConf : T[PathRef] = T {

    val content : String = os.read(filterResources().path / "profile.conf")

    val resources : String =
      s"""
         |resources = [
         |  { url="mvn:${ctResources.mvnGav()}" }
         |]
         |""".stripMargin

    val featureRefs : Seq[String] = features()
      .map(fd => fd.toConfig(scalaBinVersion()).root().render(ConfigRenderOptions.concise()) )

    val generated = content +
      resources +
      featureRefs.mkString("features = [\n", ",\n", "\n]\n") +
      "bundles = []\n"

    os.write(T.dest / "profile.conf", generated)
    PathRef(T.dest / "profile.conf")
  }

  /**
   * These are all feature repository jar files
   * @return
   */
  def featureRepos : T[Agg[Dep]] = T { Agg(features().map(_.dependency.exclude("*" -> "*")):_*) }

  /**
   * Return a sequence of all feature files used in this container. These files will be handed over
   * to the ProfileBuilder.
   */
  def featureFiles : T[Seq[PathRef]] = T {

    val dest : os.Path = T.dest

    resolveDeps(featureRepos)().iterator.foreach { repo =>
      ZipUtil.unpackZip(
        repo.path,
        dest / repo.path.last
      )
    }

    os.walk(
      path = dest,
      skip = p => {
        p.toIO.isFile() && !(p.last.endsWith(".conf"))
      }
    ).filter(_.toIO.isFile()).map(p => PathRef(p))
  }

  /**
   * The class we need to run to materialze the profile.
   */
  def profileBuilderClass : String = "blended.updater.tools.configbuilder.ProfileBuilder"

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

    ctResources.publishLocal()

    // Assemble the command line parameters
    val toolArgs : Seq[String] = Seq(
      "-f", enhanceProfileConf().path.toIO.getAbsolutePath(),
      "-o", (profileDir / "profile.conf").toIO.getAbsolutePath(),
      "--create-launch-config", (profileDir / "launch.conf").toIO.getAbsolutePath(),
      "--download-missing",
      "--update-checksums",
      "--explode-resources",
      "--maven-artifact", ctResources.mvnGav(), ctResources.jar().path.toIO.getAbsolutePath()
    ) ++
      debugArgs ++
      featureFiles().flatMap(f => Seq[String]("--feature-repo", f.path.toIO.getAbsolutePath())) ++
      artifactMap().flatMap{ case(k,v) => Seq[String]("--maven-artifact", k, v) } ++
      repoUrls.flatMap(r => Seq[String]("--maven-url", r))

    T.log.debug(s"Calling $profileBuilderClass with arguments : ${toolArgs.mkString("\n", "\n", "\n")}")

    Jvm.runSubprocess(
      mainClass = profileBuilderClass,
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

  def applyFilter : Seq[RelPath] = Seq.empty

  def profileDir(base : Path) : Command[Path] = T.command {
    base / "profiles" / profileName() / profileVersion()
  }

  /**
   * Create the runnable container by copying all resources into the right place.
   */
  def container : T [PathRef] = T {

    val ctDir : Path = T.dest

    val launcher : Path = unpackLauncher().path
    val profile : Path = materializeProfile().path

    val profileDir = ctDir / "profiles" / profileName() / profileVersion()

    os.list(launcher).iterator.foreach { p => os.copy.into(p, ctDir) }
    os.remove.all(ctDir / "META-INF")

    os.copy.into(profile / "launch.conf", ctDir)
    os.copy(profile, profileDir, createFolders = true)

    containerExtraFiles().map(_.path).foreach(CopyHelper.copyOver(_, ctDir) )
    profileExtraFiles().map(_.path).foreach(CopyHelper.copyOver(_, profileDir))

    os.remove(profileDir / "launch.conf")

    PathRef(ctDir)
  }

  /**
   * Package the runnable container into a zip archive.
   */
  def dist = T {
    val zip : Path = T.dest / "container.zip"
    ZipUtil.createZip(
      outputPath = zip,
      inputPaths = Seq(container().path),
      prefix = s"${artifactId()}-${profileVersion()}/",
      includeDirs = true
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
  object  ctResources extends BlendedBaseModule with BlendedPublishModule { base =>

    override def baseDir = ctModule.baseDir
    override def scalaVersion : T[String] = T { ctModule.scalaVersion() }
    type ProjectDeps = ctModule.ProjectDeps
    override def deps = ctModule.deps

    override def description: T[String] = T { s"Container resources for ${ctModule.description()}" }
    override def githubRepo: String = ctModule.githubRepo
    override def publishVersion: mill.T[String] = T { ctModule.publishVersion() }

    override def millSourcePath: Path = ctModule.millSourcePath / "ctResources"

    override def artifactName : T[String] = T { ctModule.artifactName() + ".resources" }

    def mvnGav : T [String] = T {
      s"${artifactMetadata().group}:${artifactMetadata().id}:${publishVersion()}"
    }

    override def resources: Sources = T.sources {

      FilterUtil.filterDirs(
        unfilteredResourcesDirs = Seq(millSourcePath / "src" / "main" / "resources"),
        pattern = ZipUtil.defaultPattern,
        filterTargetDir = T.dest,
        props = Map(
          "profile.name" -> profileName(),
          "profile.version" -> profileVersion(),
          "blended.version" -> blendedCoreVersion
        ),
        failOnMiss = false
      )

      Seq(PathRef(T.dest), PathRef(millSourcePath / "src" / "main" / "binaryResources"))
    }
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
    def baseImage : String = "blended/zulu-8-alpine:1.0.1"

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

    /**
     * The name of the docker image generated
     */
    def dockerImage : T[String]

    /**
     * The docker extra files are files that will be copied into either the container
     * directory (blended.home) or the profile directory (profile.home).
     * These files will override or complement the files that are coming from the container build.
     */
    def dockerExtrafiles : Option[Path] = None

    /**
     * Generate the dockerfile to produce the docker image
     */
    def dockerconfig : T[PathRef] = T {

      val dir = T.dest

      val ctDir : Path = dir / "files" / "container" / appFolder()
      val pDir : Path = dir / "files" / "container" / appFolder() / "profiles" / profileName() / profileVersion()

      os.copy(ctModule.container().path, ctDir, createFolders = true)

      dockerExtrafiles.foreach { p =>
        T.log.info(s"Using docker extrafiles from base directory [${p.toIO.getAbsolutePath()}]")
        CopyHelper.copyOver(p / "container", ctDir)
        CopyHelper.copyOver(p / "profile", pDir)
      }

      val content : String =
        s"""FROM $baseImage
           |LABEL maintainer="$maintainer"
           |LABEL version="${ctModule.profileVersion()}"
           |ADD --chown=$appUser:$appUser files/container /opt
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
