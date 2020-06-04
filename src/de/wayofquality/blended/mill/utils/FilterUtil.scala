package de.wayofquality.blended.mill.utils

import mill.api.Ctx
import os._

import scala.util.matching.Regex
import scala.util.matching.Regex.quoteReplacement

trait FilterUtil {
  private def applyFilter(
    source: Path,
    pattern: Regex,
    targetDir: Path,
    relative: os.RelPath,
    properties: Map[String, String],
    failOnMiss: Boolean
  )(implicit ctx: Ctx): (Path, os.RelPath) = {

    def performReplace(in: String): String = {
      val replacer = { m: Regex.Match =>
        val variable = m.group(1)
        val matched = m.matched

        quoteReplacement(properties.getOrElse(
          variable,
          if (failOnMiss) sys.error(s"Unknown variable: [$variable]") else {
            ctx.log.error(s"${source}: Can't replace unknown variable: [${variable}]")
            matched
          }
        ))
      }

      pattern.replaceAllIn(in, replacer)
    }

    val destination = targetDir / relative

    os.makeDir.all(destination / os.up)

    val content = os.read(source)
    os.write(destination, performReplace(content))

    (destination, relative)
  }

  def filterDirs(
    unfilteredResourcesDirs: Seq[Path],
    pattern: String,
    filterTargetDir: Path,
    props: Map[String, String],
    failOnMiss: Boolean
  )(implicit ctx: Ctx): Seq[(Path, RelPath)] = {
    val files: Seq[(Path, RelPath)] = unfilteredResourcesDirs.filter(os.exists).flatMap { base =>
      os.walk(base).filter(os.isFile).map(p => p -> p.relativeTo(base))
    }
    val regex = new Regex(pattern)
    val filtered: Seq[(Path, RelPath)] = files.map {
      case (file, relative) => applyFilter(file, regex, filterTargetDir, relative, props, failOnMiss)
    }
    ctx.log.debug("Filtered Resources: " + filtered.mkString(","))
    filtered
  }

}
object FilterUtil extends FilterUtil
