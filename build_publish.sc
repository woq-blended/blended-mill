import mill._
import os.Path

/** Configure plublish settings. */
trait BlendedPublishModule extends PublishModule {
  def description: String = "Blended module ${blendedModule}"
  override def publishVersion = T { blendedVersion() }

  def scpUser = T.input {
    T.env.get("WOQ_SCP_USER") match {
      case Some(u) => u
      case _ =>
        T.log.error(s"The environment variable [WOQ_SCP_USER] must be set correctly to perform a scp upload.")
        sys.exit(1)
    }
  }

  def scpKey = T.input {
    T.env.get("WOQ_SCP_KEY") match {
      case Some(k) => k
      case None =>
        T.log.error(s"The environment variable [WOQ_SCP_KEY] must be set correctly to perform a scp upload.")
        sys.exit(1)
    }
  }

  def scpHostKey = T.input {
    T.env.get("WOQ_HOST_KEY") match {
      case Some(k) => k
      case None =>
        T.log.error(s"The environment variable [WOQ_HOST_KEY] must be set correctly to perform a scp upload.")
        sys.exit(1)
    }
  }

  def scpHost : String = "u233308.your-storagebox.de"
  def scpTargetDir : String = "/"

  override def pomSettings: T[PomSettings] = T {
    PomSettings(
      description = description,
      organization = "de.wayofquality.blended",
      url = "https://github.com/woq-blended",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github("woq-blended", "blended"),
      developers = Seq(
        Developer("atooni", "Andreas Gies", "https://github.com/atooni"),
        Developer("lefou", "Tobias Roeser", "https://github.com/lefou")
      )
    )
  }

  def publishScp() : define.Command[Path] = T.command {

    val path = T.dest / blendedVersion()

    val keyFile = T.dest / "scpKey"
    val knownHosts = T.dest / "known_hosts"

    try {

      val files : Seq[Path] = new LocalM2Publisher(path)
        .publish(
          jar = jar().path,
          sourcesJar = sourceJar().path,
          docJar = docJar().path,
          pom = pom().path,
          artifact = artifactMetadata(),
          extras = extraPublish()
        )

      // Todo: Sign all files and digest

      os.write(keyFile, scpKey().replaceAll("\\$", "\n"), perms = "rw-------")
      os.write(knownHosts, s"$scpHost ssh-rsa ${scpHostKey()}")

      val process = Jvm.spawnSubprocess(
        commandArgs = Seq("scp",
          "-i", keyFile.toIO.getAbsolutePath() ,
          "-r",
          "-o", "CheckHostIP=no",
          "-o", s"UserKnownHostsFile=${knownHosts.toIO.getAbsoluteFile()}",
          path.toIO.getAbsolutePath(),s"${scpUser()}@${scpHost}:/${scpTargetDir}"
        ),
        envArgs = Map.empty,
        workingDir = baseDir
      )

      process.join()
      T.log.info(s"Uploaded ${path.toIO.getAbsolutePath()} to Blended Snapshot repo at ${scpHost}")
    } finally {
      os.remove(keyFile)
      os.remove(knownHosts)
    }
    path
  }
}