package de.wayofquality.blended.mill.utils

import java.io.FileOutputStream
import java.util.zip.ZipEntry

import os.Path

trait ZipUtil {

  def createZip(outputPath: os.Path,
                inputPaths: Seq[Path],
                fileFilter: (os.Path, os.RelPath) => Boolean = (p: os.Path, r: os.RelPath) => true,
                prefix: String = "",
                timestamp: Option[Long] = None,
                includeDirs: Boolean = false): Unit = {
    import java.util.zip.ZipOutputStream
    import scala.collection.mutable

    os.remove.all(outputPath)
    val seen = mutable.Set.empty[os.RelPath]
    val zip = new ZipOutputStream(new FileOutputStream(outputPath.toIO))

    try{
      assert(inputPaths.forall(os.exists(_)))
      for{
        p <- inputPaths
        (file, mapping) <-
          (if (os.isFile(p)) Iterator(p -> os.rel / p.last)
           else os.walk(p).iterator.withFilter(_ => includeDirs).map(sub => sub -> sub.relativeTo(p)))
        if !seen(mapping) && fileFilter(p, mapping)
      } {
        if (os.isFile(file)) {
          seen.add(mapping)
          val entry = new ZipEntry(prefix + mapping.toString)
          entry.setTime(timestamp.getOrElse(os.mtime(file)))
          zip.putNextEntry(entry)
          if(os.isFile(file)) zip.write(os.read.bytes(file))
          zip.closeEntry()
        }
      }
    } finally {
      zip.close()
    }
  }

  def unpackZip(src: os.Path, dest: os.Path): Unit = {
    import mill.api.IO

    os.makeDir.all(dest)
    val byteStream = os.read.inputStream(src)
    val zipStream = new java.util.zip.ZipInputStream(byteStream)
    try {
      while ({
        zipStream.getNextEntry match {
          case null => false
          case entry =>
            if (!entry.isDirectory) {
              val entryDest = dest / os.RelPath(entry.getName)
              os.makeDir.all(entryDest / os.up)
              val fileOut = new java.io.FileOutputStream(entryDest.toString)
              try IO.stream(zipStream, fileOut)
              finally fileOut.close()
            }
            zipStream.closeEntry()
            true
        }
      }) ()
    }
    finally zipStream.close()
  }
}
object ZipUtil extends ZipUtil
