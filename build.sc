import mill._
import mill.scalalib._

//import $file.build_publish
//import build_publish.BlendedPublishModule

import $file.GitModule
import GitModule.GitModule

val baseDir : os.Path = build.millSourcePath

trait Deps {
  def millVersion : String

  def millMain = ivy"com.lihaoyi::mill-main:${millVersion}"
  def millScalalib = ivy"com.lihaoyi::mill-scalalib:${millVersion}"
}

object Deps_0_7 extends Deps {
  override def millVersion = "0.7.3"
}

object GitSupport extends GitModule {
  override def millSourcePath: os.Path = baseDir
}

def pluginVersion = T.input {
  val v = T.env.get("CI") match {
    case Some(ci @ ("1" | "true")) =>
      val version = GitSupport.publishVersion()._2
      T.log.info(s"Using git-based version: ${version} (CI=${ci})")
      version
    case _ => os.read(baseDir / "version.txt").trim()
  }
  val path = T.dest / "version.txt"
  os.write(path, v)
  v
}

object plugin extends ScalaModule {

  override def scalaVersion : T[String] = "2.13.2"
  override def millSourcePath : os.Path = baseDir / "webtools"

  override def ivyDeps = T { super.ivyDeps() ++ Agg(
    Deps_0_7.millMain,
    Deps_0_7.millScalalib
  )}
}
