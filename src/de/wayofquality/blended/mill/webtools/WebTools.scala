package de.wayofquality.blended.mill.webtools

import mill._
import os._
import mill.modules.Jvm

trait WebTools extends Module {

  def npmModulesDir : Path

  def yarnInstall : T[PathRef] = T {
    val process = Jvm.spawnSubprocess(
      commandArgs = Seq(
        "yarn", "install", "--modules-folder", npmModulesDir.toIO.getAbsolutePath()
      ),
      envArgs = Map.empty,
      workingDir = npmModulesDir
    )
    process.join()
    T.log.info(new String(process.stdout.bytes))
    PathRef(npmModulesDir)
  }

  def webPackConfig : Path = millSourcePath / "docs.webpack.config.js"

  def prepareWebPackConfig : T[PathRef] = T {

    val destFile = T.dest / webPackConfig.last
    os.copy(webPackConfig, destFile)
    PathRef(destFile)
  }

  def webpack : T[PathRef] = T {

    val out : Path = T.dest

    yarnInstall()

    val process = Jvm.spawnSubprocess(
      commandArgs = Seq(
        (npmModulesDir/ "webpack-cli" / "bin" / "cli.js").toIO.getAbsolutePath(),
        "--output-path", out.toIO.getAbsolutePath(),
        "--config", prepareWebPackConfig().path.toIO.getAbsolutePath()
      ),
      envArgs = Map.empty,
      workingDir = millSourcePath
    )
    process.join()
    T.log.info(new String(process.stdout.bytes()))
    PathRef(out)
  }
}
