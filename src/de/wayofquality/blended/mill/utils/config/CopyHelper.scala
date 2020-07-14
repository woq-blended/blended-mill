package de.wayofquality.blended.mill.utils.config

import os.Path

object CopyHelper {
  /**
   * Helper to copy the content of a given directory into a destination directory.
   * The given directory may not be present (i.e. no extra files are required).
   * Files within the destination folder will be overwritten by the content of the
   * given directory.
   */
  def copyOver(src: Path, dest: Path) : Unit = {
    if (src.toIO.exists()) {
      os.walk(src).foreach { p =>
        if (p.toIO.isFile()) {
          os.copy(p, dest / p.relativeTo(src), replaceExisting = true, createFolders = true)
        }
      }
    }
  }
}
