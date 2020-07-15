package de.wayofquality.blended.mill.utils

import java.io._

import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveOutputStream}
import os.{Path, RelPath}

object TarUtil {

  def untar(srcTar: Path, dest : Path) : Path = {

    os.makeDir.all(dest)

    val is : InputStream = new FileInputStream(srcTar.toIO.getAbsoluteFile())
    val tar = new ArchiveStreamFactory()
      .createArchiveInputStream(new BufferedInputStream(is))

    var entry = Option(tar.getNextEntry())

    while (entry.isDefined) {
      entry match {
        case Some(f) =>
          val rp : RelPath = RelPath(f.getName().split("/").toIndexedSeq, 0)
          if (f.isDirectory()) {
            os.makeDir.all(dest / rp)
          } else {
            val content : Array[Byte] = StreamCopy.read(tar)
            os.write(dest / rp, content)
          }

          entry = Option(tar.getNextEntry())
        case None => // do nothing
      }
    }

    tar.close()
    is.close()

    dest
  }

  def tar(file: File, os: OutputStream, user: Int = 0, group: Int = 0): Unit = {

    def addFileToTar(tarOs: TarArchiveOutputStream, file: File, base: String): Unit = {
      val entryName = base + file.getName()
      val entry = new TarArchiveEntry(file, entryName)

      entry.setUserId(user)
      entry.setGroupId(group)

      tarOs.putArchiveEntry(entry)

      if (file.isFile()) {
        StreamCopy.copy(new FileInputStream(file), tarOs)
        tarOs.closeArchiveEntry()
      } else {
        tarOs.closeArchiveEntry()

        val files = Option(file.listFiles())
        files.map { ff =>
          ff.foreach { f =>
            addFileToTar(tarOs, f, entryName + "/")
          }
        }
      }
    }

    if (!file.exists()) throw new FileNotFoundException(file.getAbsolutePath())

    val bOut = new BufferedOutputStream(os)
    val tarOut = new TarArchiveOutputStream(bOut)

    try {
      addFileToTar(tarOut, file, "")
    } finally {
      tarOut.finish()
      tarOut.close()
      bOut.close()
      os.close()
    }
  }
}


