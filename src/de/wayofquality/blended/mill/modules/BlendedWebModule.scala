package de.wayofquality.blended.mill.modules

import mill._
import mill.api.Loose
import mill.scalalib.Dep
import mill.define.{Sources, Target}
import de.wayofquality.blended.mill.publish.BlendedPublishModule

trait BlendedWebModule extends BlendedBaseModule with BlendedOsgiModule { m : BlendedPublishModule =>

  def blendedCoreVersion : String

  // This is usually produced by a packageHtml Step
  def webContent : T[PathRef]
  // The directory within the bundle that has all the content
  def contentDir : String = "webapp"
  def webContext : String

  def bundleActivator : String

  def activatorPackage : String = {
    assert(bundleActivator.contains("."))
    bundleActivator.substring(0, bundleActivator.lastIndexOf("."))
  }

  def activatorClass : String = {
    bundleActivator.substring(bundleActivator.lastIndexOf(".") + 1)
  }

  def generatedActivator =
    s"""package $activatorPackage
       |
       |import blended.akka.http._
       |
       |class $activatorClass extends WebBundleActivator {
       |  val contentDir = "$contentDir"
       |  val contextName = "$webContext"
       |}
       |""".stripMargin

  override def bundleSymbolicName = artifactName

  override def osgiHeaders = T {
    val scalaBinVersion = scalaVersion().split("[.]").take(2).mkString(".")
    super.osgiHeaders().copy(
      `Import-Package` =
        Seq(s"""scala.*;version="[${scalaBinVersion}.0,${scalaBinVersion}.50]"""") ++
          Seq("*"),
      `Bundle-Activator` = Some(bundleActivator)
    )
  }

  override def ivyDeps: Target[Loose.Agg[Dep]] = T { super.ivyDeps() ++ Agg(
    deps.blendedDep(blendedCoreVersion)("akka.http")
  )}

  override def generatedSources = T {

    val generated = T.dest / "generatedSources"
    os.makeDir.all(generated)
    os.write(generated / s"$activatorClass.scala", generatedActivator)
    super.generatedSources() ++ Seq(PathRef(generated))
  }

  def webResources : T[PathRef] = T {
    val content = T.dest / "content"
    os.makeDir.all(content)
    os.copy(webContent().path, content / contentDir)
    PathRef(content)
  }

  override def resources : Sources = T.sources {
    super.resources() ++ Seq(webResources())
  }
}