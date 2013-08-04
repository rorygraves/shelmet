package org.shelmet.heap.util

import scala.xml.Utility

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

  def encodeHtml(str: String): String = Utility.escape(str)
}