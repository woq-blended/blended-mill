package de.wayofquality.blended.mill.feature

import de.wayofquality.blended.mill.modules.BlendedBaseModule

import mill._
import mill.scalalib.PublishModule

trait BlendedFeatureJar extends BlendedBaseModule { jar : PublishModule =>

  def features : T[Seq[FeatureModule]] = T{ Seq.empty[FeatureModule] }
  
  override def resources = T.sources {
    
    val dest = T.ctx.dest
    os.makeDir.all(dest / "features")

    features().foreach{ f => 
      val conf : String = f.featureConf(publishVersion(), scalaBinVersion())
      os.write(dest / "features" / s"${f.name}.conf", conf)
    }
    
    super.resources() ++ Seq(PathRef(dest))
  }

}