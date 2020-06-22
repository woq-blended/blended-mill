package de.wayofquality.blended.mill.feature

import mill._
import de.wayofquality.blended.mill.modules.BlendedBaseModule

trait BlendedFeatureModule extends BlendedBaseModule { base =>

  def version : T[String]
  override def artifactName = T { millModuleSegments.parts.mkString(".") }

  /**
    * Features that are required to load this feature.
    */
  def featureDeps : T[Seq[FeatureRef]] = T { Seq.empty[FeatureRef] }

  /**
    * Bundles defining this particular feature
    */

  def featureBundles : T[Seq[FeatureBundle]] = T { Seq.empty[FeatureBundle] }

  override def ivyDeps = T {
    featureDeps().map(_.dependency) ++ featureBundles().map(_.dependency)
  }

  def featureConf : T[PathRef] = T {

    val bundleConf : String = featureBundles()
      .map(_.formatConfig(scalaBinVersion()))
      .mkString(",\n")

    val featureConf : String = if (featureDeps().isEmpty) {
      ""
    } else {
      featureDeps().map{ fd => 
        s"""    { name = "${fd.dependency.dep.module.name.value}", version = "${fd.dependency.dep.version}" }"""
      }.mkString("  features = [\n", ",\n", "\n  ]")
    }

    val conf = T.dest / s"${blendedModule}.conf"

    val content =
      s"""{
         |  name = "${ blendedModule }"
         |  version = "${version()}"
         |""".stripMargin + featureConf +
         """
         |  bundles = [
         |""".stripMargin + bundleConf +
         """
          |  ]
          |}""".stripMargin

    os.write(conf, content)

    PathRef(conf)
  }
}