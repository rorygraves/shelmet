package org.shelmet.heap.util

/**
 * Miscellaneous functions I couldn't think of a good place to put.
 */
object Misc {

  def toHex(address: Long): String = {
    "0x%x".format(address)
  }

  def parseHex(value: String): Long = {
    java.lang.Long.decode(value)
  }

  val charMap: Map[Char, String] = Map('<' -> "&lt;", '>' -> "&gt;", '"' -> "&quot;", '&' -> "&amp;", '\'' -> "&#039;")

  def encodeHtml(str: String): String = {
    // TODO this should be replaceable with a library method
    val buf: StringBuilder = new StringBuilder

    str foreach {
      ch =>
        if (charMap.contains(ch))
          buf.append(charMap.get(ch).get)
        else
        if (ch < ' ') {
          buf.append("&#").append(Integer.toString(ch)).append(";")
        }
        else {
          val c: Int = ch & 0xFFFF
          if (c > 127) {
            buf.append("&#").append(Integer.toString(c)).append(";")
          }
          else {
            buf.append(ch)
          }
        }
    }
    buf.toString()
  }

}