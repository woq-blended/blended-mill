package de.wayofquality.blended.mill.utils

import java.io._
import java.util.zip.GZIPOutputStream

import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveOutputStream, TarConstants}
import os.{Path, RelPath}

import scala.collection.mutable
import scala.util.Try

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

  def tar(
    outputPath: Path,
    inputPaths: Seq[Path],
    fileFilter: (os.Path, os.RelPath) => Boolean = (p: os.Path, r: os.RelPath) => true,
    prefix: String = "",
    includeDirs: Boolean = false,
    user : Int = 0,
    group : Int = 0
  ): Try[Path] = Try {

    os.remove.all(outputPath)
    val seen = mutable.Set.empty[os.RelPath]
    val fos : OutputStream = new FileOutputStream(outputPath.toIO)
    val zos : OutputStream = new GZIPOutputStream(fos)
    val tar = new TarArchiveOutputStream(zos)

    tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
    tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)

    try{
      assert(inputPaths.forall(os.exists(_)))
      for{
        p <- inputPaths
        (file, mapping) <-
          (if (os.isFile(p)) Iterator(p -> os.rel / p.last)
          else os.walk(p).iterator.withFilter(_ => includeDirs).map(sub => sub -> sub.relativeTo(p)))
        if !seen.contains(mapping)
      } {
        seen.add(mapping)
        if (os.isFile(file)) {
          val entry = new TarArchiveEntry(prefix + mapping.toString)
          entry.setSize(os.size(file))
          entry.setUserId(user)
          entry.setGroupId(group)
          tar.putArchiveEntry(entry)
          if(os.isFile(file)) tar.write(os.read.bytes(file))
          tar.closeArchiveEntry()
        } else if (os.isDir(file)) {
          val entry = new TarArchiveEntry(prefix + mapping.toString, TarConstants.LF_DIR)
          entry.setUserId(user)
          entry.setGroupId(group)
          tar.putArchiveEntry(entry)
          tar.closeArchiveEntry()
        }
      }
      outputPath
    } finally {
      tar.close()
      zos.close()
      fos.close()
    }
  }
}


