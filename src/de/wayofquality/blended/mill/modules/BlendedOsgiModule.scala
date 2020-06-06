package de.wayofquality.blended.mill.modules

import de.tobiasroeser.mill.osgi.{OsgiBundleModule, OsgiHeaders}
import de.wayofquality.blended.mill.publish.BlendedPublishModule
import mill._

trait BlendedOsgiModule extends OsgiBundleModule { this : BlendedBaseModule with BlendedPublishModule =>

  override def bundleSymbolicName = blendedModule

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