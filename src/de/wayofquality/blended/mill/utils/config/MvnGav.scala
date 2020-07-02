package de.wayofquality.blended.mill.utils.config

import java.util.regex.Pattern

import coursier.core.Publication
import mill.scalalib._

import scala.util.Try

case class MvnGav(
 group : String,
 artifact : String,
 version : String,
 classifier : Option[String] = None,
 fileExt : String = "jar"
) {

  def asDep(scalaBinVersion : String) : Dep = {

    val (artifactName : String, isScala : Boolean) = if (artifact.endsWith("_" + scalaBinVersion)) {
      (artifact.substring(0, artifact.lastIndexOf("_")), true)
    } else {
      (artifact, false)
    }

    val d : Dep = ivy"$group:${if (isScala) ":" else ""}$artifactName:$version"

    val p : Publication = d.dep.publication

    val pWithExt : Publication = if (fileExt != "jar" && fileExt.nonEmpty) {
      p.withType(coursier.core.Type(fileExt)).withExt(coursier.core.Extension(fileExt))
    } else {
      p
    }

    val pWithClass : Publication = classifier match {
      case None => pWithExt
      case Some(c) => pWithExt.withClassifier(coursier.core.Classifier(c))
    }

    d.copy(dep = d.dep.withPublication(pWithClass))
  }
}

object MvnGav {

  val GroupIdToPathPattern = Pattern.compile("[.]")
  val ParseCompactPattern = Pattern.compile("([^:]+)[:]([^:]+)[:]([^:]+)")
  val ParseFullPattern = Pattern.compile("([^:]+)[:]([^:]+)[:]([^:]*)[:]([^:]+)([:]([^:]+))?")

  def parse(gav : String) : Try[MvnGav] = Try {
    val m = ParseCompactPattern.matcher(gav)
    if (m.matches()) {
      MvnGav(
        group = m.group(1),
        artifact = m.group(2),
        version = m.group(3))
    } else {
      val m2 = ParseFullPattern.matcher(gav)
      if (m2.matches()) {
        val classifier = m2.group(3) match {
          case "" | "jar" => None
          case c          => Some(c)
        }
        val fileExt = m2.group(6) match {
          case null | "" if classifier == Some("pom") => "pom"
          case null | ""                              => "jar"
          case t                                      => t
        }
        MvnGav(
          group = m2.group(1),
          artifact = m2.group(2),
          version = m2.group(4),
          classifier = classifier,
          fileExt = fileExt
        )
      } else
        sys.error("Invalid GAV coordinates: " + gav)
    }
  }

}
