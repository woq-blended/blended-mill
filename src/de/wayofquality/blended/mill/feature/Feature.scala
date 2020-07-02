package de.wayofquality.blended.mill.feature

import com.typesafe.config.{Config, ConfigFactory, ConfigValue, ConfigValueFactory}
import mill.scalalib.Dep

import scala.jdk.CollectionConverters._
import de.wayofquality.blended.mill.utils.config.Implicits._
import de.wayofquality.blended.mill.utils.config.MvnGav

import scala.util.Try

/**
  * Featurebundles represent a single jar that is deployed within a container
  */
object FeatureBundle {
  def apply(dependency : Dep) : FeatureBundle =
    FeatureBundle(dependency, startLevel = None, start = false)

  def apply(dependency : Dep, level : Int, start : Boolean) : FeatureBundle =
    FeatureBundle(dependency, startLevel = Some(level), start = start)

  def fromConfig(scalaBinVersion : String)(cfg : Config) : Try[FeatureBundle] = Try {
    val url : String = cfg.getString("url").substring(4)
    val dep : Dep = MvnGav.parse(url).map(_.asDep(scalaBinVersion)).get

    FeatureBundle(
      dependency = dep,
      startLevel = cfg.getIntOption("startLevel"),
      start = cfg.getBoolean("start", false)
    )
  }

  implicit def rw : upickle.default.ReadWriter[FeatureBundle] = upickle.default.macroRW
}

case class FeatureBundle private (
  dependency: Dep,
  startLevel: Option[Int],
  start: Boolean
) {

  def toConfig(scalaBinVersion : String): Config = {

    val url : String = s"mvn:" + GAVHelper.gav(scalaBinVersion)(dependency)

    ConfigFactory.empty()
      .withValue("url", ConfigValueFactory.fromAnyRef(url))
      .setOptionInt("startLevel", startLevel)
      .setOptionAnyRef("start", if (start) {Some(true.asInstanceOf[AnyRef])} else {None})
  }
}

/**
  * FeatureReferences represents a single module contained in a jar file of multiple feature module configs.
  */
object FeatureRef {

  def fromConfig(scalaBinVersion : String)(cfg : Config) : Try[FeatureRef] = Try {
    val url : String = cfg.getString("url").substring(4)

    FeatureRef(
      dependency = MvnGav.parse(url).map(_.asDep(scalaBinVersion)).get,
      names = cfg.getStringList("names", List.empty[String])
    )
  }

  implicit def rw : upickle.default.ReadWriter[FeatureRef] = upickle.default.macroRW
}

case class FeatureRef(
  dependency : Dep,
  names : Seq[String]
) {

  def toConfig(scalaBinVersion : String) : Config = {

    val url : String = "mvn:" + GAVHelper.gav(scalaBinVersion)(dependency)

    ConfigFactory.empty()
      .withValue("url", ConfigValueFactory.fromAnyRef(url))
      .withValue("names", ConfigValueFactory.fromIterable(names.asJava))
  }
}

/**
  * A feature module describes a feature with references to bundles and other feature modules.
  * From this information the individual feature configs are generated.
  */
object Feature {

  def fromConfig(scalaBinVersion : String)(cfg : Config) : Try[Feature] = Try {

    val features : Seq[FeatureRef] = cfg.getConfigList("features", List.empty)
      .map(c => FeatureRef.fromConfig(scalaBinVersion)(c).get)

    val bundles : Seq[FeatureBundle] = cfg.getConfigList("bundles", List.empty)
      .map(c => FeatureBundle.fromConfig(scalaBinVersion)(c).get)

    Feature(
      repoUrl = cfg.getString("repoUrl"),
      name = cfg.getString("name"),
      features = features,
      bundles = bundles
    )
  }

  implicit def rw : upickle.default.ReadWriter[Feature] = upickle.default.macroRW
}

case class Feature(
  repoUrl : String,
  name : String,
  features : Seq[FeatureRef],
  bundles : Seq[FeatureBundle]
) {

  def toConfig(version : String, scalaBinVersion : String) : Config = {

    val bundleConf : ConfigValue = ConfigValueFactory.fromIterable(
      bundles.map(_.toConfig(scalaBinVersion).root()).asJava
    )

    val cfg : Config = if (features.isEmpty) {
      ConfigFactory.empty()
    } else {
      ConfigFactory.empty()
        .withValue("features", ConfigValueFactory.fromIterable(
          features.map(_.toConfig(scalaBinVersion).root()).asJava
        ))
    }

    cfg
      .setOptionString("repoUrl", Some(repoUrl))
      .setOptionString("name", Some(name))
      .setOptionString("version", Some(version))
      .withValue("bundles", bundleConf)
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