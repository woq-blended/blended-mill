import mill._
import mill.define.Target
import mill.scalalib._
import os.Path

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

def pluginVersion: T[String] = T { GitSupport.publishVersion() }

trait PluginModule extends ScalaModule with BlendedPublishModule {

  def basePackage : String = "de.wayofquality.blended.mill"
  /**
   * Sources to cop into the plugin itself. The right hand side of the Map
   * indicates the target package.
   */
  def pluginSources : Map[String, String] = Map(
    "GitModule" -> "versioning",
    "BlendedPublish" -> "publish"
  )

  override def scalacOptions = Seq(
    "--deprecation",
    "--target:8",
    "-Werror",
    "--feature",
    Seq(
      "adapted-args",
      "constant",
      "deprecation",
      "doc-detached",
      "inaccessible",
      "infer-any",
      "missing-interpolator",
      "nullary-override",
      "nullary-unit",
      "option-implicit",
      "poly-implicit-overload",
      "stars-align",
      // Compiler doesn't know it but suggests it: "Recompile with -Xlint:unchecked for details."
      // "unchecked",
      "unused",
    ).mkString("-Xlint:", ",", ""),
    //    "--unchecked"
  )

  override def publishVersion: T[String] = T { pluginVersion() }

  override def description : String = "A collection of utilities for building blended projects with mill"

  override def generatedSources: Target[Seq[PathRef]] = T {

    val dir = T.dest
    pluginSources.foreach { case (f, p) =>
      val content : String = os.read(baseDir / s"$f.sc")

      val t : Path = T.dest / s"$f.scala"
      os.write(t, s"package $basePackage.$p\n\n$content")
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

