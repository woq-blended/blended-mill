package de.wayofquality.blended.mill.feature

import mill.scalalib.Dep

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

  def gav(scalaBinVersion : String) : String = {

    val classifier : String = dependency.dep.attributes.classifier.value
    val ext : String = dependency.dep.publication.ext.value
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

  def formatConfig(scalaBinVersion : String): String = {

    val builder: StringBuilder = new StringBuilder("    { ")

    builder.append("url=\"mvn:")
    builder.append(gav(scalaBinVersion))
    builder.append("\"")

    startLevel.foreach { sl => builder.append(s", startLevel=${sl}") }
    if (start) builder.append(", start=true")

    builder.append(" }")

    builder.toString()
  }
}
