package de.wayofquality.blended.mill.feature

import com.typesafe.config.ConfigRenderOptions
import de.wayofquality.blended.mill.modules.BlendedBaseModule
import mill._
import mill.scalalib.PublishModule

trait BlendedFeatureJar extends BlendedBaseModule { jar : PublishModule =>

  def features : T[Seq[Feature]] = T{ Seq.empty[Feature] }

  override def resources = T.sources {

    val dest = T.ctx.dest
    os.makeDir.all(dest / "features")

    features().foreach{ f =>
      val conf : String =
        f.toConfig(publishVersion(), scalaBinVersion())
          .root()
          .render(ConfigRenderOptions.defaults().setFormatted(true).setComments(false).setOriginComments(false))
      os.write(dest / "features" / s"${f.name}.conf", conf)
    }

    super.resources() ++ Seq(PathRef(dest))
  }

}