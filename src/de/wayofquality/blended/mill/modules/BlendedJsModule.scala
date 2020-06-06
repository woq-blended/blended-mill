package de.wayofquality.blended.mill.modules

import de.wayofquality.blended.mill.publish.BlendedPublishModule
import mill._
import mill.define.Sources
import mill.scalajslib.ScalaJSModule
import mill.scalajslib.api.ModuleKind

trait BlendedJsModule extends BlendedBaseModule with ScalaJSModule { jsBase : BlendedPublishModule =>

  override def moduleKind: T[ModuleKind] = T{ ModuleKind.CommonJSModule }

  override def scalaVersion = deps.scalaVersion
  override def scalaJSVersion: T[String] = deps.scalaJsVersion

  override def toolsClasspath = {
    scoverageReportWorkerClasspath() ++ scoverageClasspath() ++
    scalaJSWorkerClasspath() ++ scalaJSLinkerClasspath()
  }

  trait BlendedJsTests extends super.Tests {
    def blendedTestModule : String = jsBase.blendedModule + ".test"
    override def artifactName = blendedTestModule

    override def millSourcePath = jsBase.millSourcePath / "src" / "test"

    override def sources: Sources = T.sources(
      millSourcePath / "scala"
    )
    override def ivyDeps = T{ super.ivyDeps() ++ Agg(
      jsBase.deps.js.scalatest
    )}

    override def testFrameworks = Seq("org.scalatest.tools.Framework")

    override def moduleKind = jsBase.moduleKind
  }
}