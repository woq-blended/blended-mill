import mill._
import mill.define.Target
import mill.scalalib._
import os.Path

//import $file.build_publish
//import build_publish.BlendedPublishModule

import $file.GitModule
import GitModule.GitModule

import $file.BlendedPublish
import BlendedPublish.BlendedPublishModule

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

trait PluginModule extends ScalaModule with BlendedPublishModule {

  /**
   * Sources to cop into the plugin itself. The right hand side of the Map
   * indicates the target package.
   */
  def pluginSources : Map[String, String] = Map(
    "GitModule" -> "blended.mill.versioning",
    "BlendedPublish" -> "blended.mill.publish"
  )

  override def publishVersion: T[String] = T { pluginVersion() }

  override def description : String = "A collection of utilities for building blended projects with mill"

  override def generatedSources: Target[Seq[PathRef]] = T {

    val dir = T.dest
    pluginSources.foreach { case (f, p) =>
      val content : String = os.read(baseDir / s"$f.sc")

      val t : Path = T.dest / s"$f.scala"
      os.write(t, s"package $p\n\n$content")
    }

    super.generatedSources() ++ Seq(PathRef(dir))
  }
}

object blended extends Module {
  object mill extends PluginModule {

    override def scalaVersion : T[String] = "2.13.2"
    override def millSourcePath : os.Path = baseDir

    override def ivyDeps = T { super.ivyDeps() ++ Agg(
      Deps_0_7.millMain,
      Deps_0_7.millScalalib
    )}
  }
}

