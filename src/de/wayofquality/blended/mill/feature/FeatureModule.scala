package de.wayofquality.blended.mill.feature

import mill.scalalib.Dep 

/**
  * Featurebundles represent a single jar that is deployed within a container
  */
object FeatureBundle {
  def apply(dependency : Dep) : FeatureBundle =
    FeatureBundle(dependency, startLevel = None, start = false)

  def apply(dependency : Dep, level : Int, start : Boolean) : FeatureBundle =
    FeatureBundle(dependency, startLevel = Some(level), start = start)

  implicit def rw : upickle.default.ReadWriter[FeatureBundle] = upickle.default.macroRW
}

case class FeatureBundle private (
  dependency: Dep,
  startLevel: Option[Int],
  start: Boolean
) {

  def formatConfig(scalaBinVersion : String): String = {

    val builder: StringBuilder = new StringBuilder("    { ")

    builder.append("url=\"mvn:")
    builder.append(GAVHelper.gav(scalaBinVersion)(dependency))
    builder.append("\"")

    startLevel.foreach { sl => builder.append(s", startLevel=${sl}") }
    if (start) builder.append(", start=true")

    builder.append(" }")

    builder.toString()
  }
}

/**
  * FeatureReferences represents a single module contained in a jar file of multiple feature module configs. 
  */
object FeatureRef {
  implicit def rw : upickle.default.ReadWriter[FeatureRef] = upickle.default.macroRW 
}

case class FeatureRef(
  dependency : Dep,
  names : Seq[String]
)

/**
  * A feature module describes a feature with references to bundles and other feature modules. 
  * From this information the indiviadual feature configs are generated.
  */
object FeatureModule {
  implicit def rw : upickle.default.ReadWriter[FeatureModule] = upickle.default.macroRW 
}

case class FeatureModule(
  name : String,
  features : Seq[FeatureRef],    
  bundles : Seq[FeatureBundle]    
) {

  def featureConf(version : String, scalaBinVersion : String) : String = {

    val bundleConf : String = bundles
      .map(_.formatConfig(scalaBinVersion))
      .mkString(",\n")

    val featureConf : String = if (features.isEmpty) {
      ""
    } else {
      features.map{ fd => 
        s"""    { url="mvn:${GAVHelper.gav(scalaBinVersion)(fd.dependency)}"
           |      names = [${fd.names.map(s => "\"" + s + "\"").mkString(",")}]
           |      version = "${fd.dependency.dep.version}" }""".stripMargin
      }.mkString("  features = [\n", ",\n", "\n  ]")
    }

    s"""{
        |  name = "${ name }"
        |  version = "${version}"
        |""".stripMargin + featureConf +
        """
        |  bundles = [
        |""".stripMargin + bundleConf +
        """
        |  ]
        |}""".stripMargin

  }
}

object GAVHelper {

  def gav(scalaBinVersion : String)(dependency : Dep) : String = {

   val classifier : String = dependency.dep.attributes.classifier.value
    val ext : String = dependency.dep.publication.`type`.value
    val fullFormat : Boolean = classifier.nonEmpty ||  !List("", "jar").contains(ext)

    val builder : StringBuilder = new StringBuilder(dependency.dep.module.toString())
    if (dependency.cross.isBinary){
      builder.append(s"_$scalaBinVersion")
    }

    builder.append(":")

    if (fullFormat) {
      builder.append(classifier)
      builder.append(":")
      builder.append(dependency.dep.version)
      builder.append(":")
      builder.append(ext)
    } else {
      builder.append(dependency.dep.version)
    }

    builder.toString()
  }
}