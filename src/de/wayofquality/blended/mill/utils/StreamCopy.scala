package de.wayofquality.blended.mill.utils

import java.io.{ByteArrayOutputStream, InputStream, OutputStream}

object StreamCopy {

  /**
   * Copy a [[InputStream]] `in` to the [[OutputStream]] `out`.
   * This methods blocks as long as the input stream is open.
   * It's the callers responsibility to properly create and close the output stream.
   */
  def copy(in: InputStream, out: OutputStream): Unit = {
    val buf = new Array[Byte](1024)
    var len = 0
    while ({
      len = in.read(buf)
      len > 0
    }) {
      out.write(buf, 0, len)
    }
    out.flush()
  }

  def read(in : InputStream) : Array[Byte] = {
    val os : ByteArrayOutputStream = new ByteArrayOutputStream()
    copy(in, os)
    os.toByteArray
  }
}