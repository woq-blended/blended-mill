package de.wayofquality.blended.mill.modules

import de.tobiasroeser.mill.osgi.{OsgiBundleModule, OsgiHeaders}
import de.wayofquality.blended.mill.publish.BlendedPublishModule
import mill._

trait BlendedOsgiModule extends OsgiBundleModule { this : BlendedBaseModule with BlendedPublishModule =>

  // TODO: including the scala binary version requires a bit more thought when resolving configurations 
  override def bundleSymbolicName = T { blendedModule } //+ "_" + scalaBinVersion()}

  override def osgiHeaders: T[OsgiHeaders] = T{
    super.osgiHeaders().copy(
      `Export-Package` = exportPackages,
      `Import-Package` =
        // scala compatible binary version control
        Seq(s"""scala.*;version="[${scalaBinVersion()}.0,${scalaBinVersion()}.50]"""") ++
          essentialImportPackage ++
          Seq("*")
    )
  }
}
