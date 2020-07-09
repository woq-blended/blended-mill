import $exec.plugins

import mill._
import de.wayofquality.blended.mill.modules._

def verify() = T.command {
  assert(BlendedDependencies.Deps_2_13.scalaVersion.startsWith("2.13"))
  ()
}
