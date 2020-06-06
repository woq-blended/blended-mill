package de.wayofquality.blended.mill.modules

import de.wayofquality.blended.mill.publish.BlendedPublishModule
import mill.{Agg, PathRef, T}
import mill.define.Sources
import mill.scalajslib.ScalaJSModule
import mill.scalajslib.api.ModuleKind

trait BlendedJvmModule extends BlendedBaseModule { jvmBase : BlendedPublishModule =>
  override def millSourcePath = super.millSourcePath / "jvm"
  override def sources = T.sources {
    super.sources() ++ Seq(PathRef(millSourcePath / os.up / "shared" / "src" / "main" / "scala"))
  }
  override def resources = T.sources { super.resources() ++ Seq(
    PathRef(millSourcePath / os.up / "shared" / "src" / "main" / "resources"),
    PathRef(millSourcePath / os.up / "shared" / "src" / "main" / "binaryResources")
  )}

  trait CoreJvmTests extends super.BlendedTests {
    override def sources = T.sources {
      super.sources() ++ Seq(PathRef(jvmBase.millSourcePath / os.up / "shared" / "src" / "test" / "scala"))
    }
    override def testResources = T.sources { super.testResources() ++ Seq(
      PathRef(jvmBase.millSourcePath / os.up / "shared" / "src" / "test" / "resources"),
      PathRef(jvmBase.millSourcePath / os.up / "shared" / "src" / "test" / "binaryResources")
    )}
  }

  trait BlendedJs extends ScalaJSModule { jsBase : BlendedPublishModule =>
    override def millSourcePath = jvmBase.millSourcePath / os.up / "js"
    override def scalaJSVersion = deps.scalaJsVersion
    override def scalaVersion = jvmBase.scalaVersion
    override def sources: Sources = T.sources(
      millSourcePath / "src" / "main" / "scala",
      millSourcePath / os.up / "shared" / "src" / "main" / "scala"
    )
    override def moduleKind: T[ModuleKind] = T{ ModuleKind.CommonJSModule }
    def blendedModule = jvmBase.blendedModule
    override def artifactName: T[String] = jvmBase.artifactName
    trait CoreJsTests extends super.Tests {
      override def sources: Sources = T.sources(
        jsBase.millSourcePath / "src" / "test" / "scala"
      )
      override def ivyDeps = T{ super.ivyDeps() ++ Agg(
        deps.js.scalatest
      )}
      override def testFrameworks = Seq("org.scalatest.tools.Framework")
      override def moduleKind: T[ModuleKind] = T{ ModuleKind.CommonJSModule }
    }
  }
}
