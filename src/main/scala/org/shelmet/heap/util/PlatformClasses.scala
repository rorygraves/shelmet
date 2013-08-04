package org.shelmet.heap.util

import org.shelmet.heap.model.JavaClass

/**
* This class is a helper that determines if a class is a "platform"
* class or not.  It's a platform class if its name starts with one of
* the prefixes to be found in /com/sun/tools/hat/resources/platform_names.txt.
*/
object PlatformClasses {

  val platformPrefixes = Set(
    "boolean[",
    "char[",
    "float[",
    "double[",
    "byte[",
    "short[",
    "int[",
    "long[",
    "sun.",
    "java.",
    "javax.accessibility",
    "javax.crypto.",
    "javax.imageio.",
    "javax.naming.",
    "javax.net.",
    "javax.print.",
    "javax.rmi.",
    "javax.security.",
    "javax.sound.",
    "javax.sql.",
    "javax.swing.",
    "javax.transaction.",
    "javax.xml.parsers.",
    "javax.xml.transform.",
    "org.ietf.jgss.",
    "org.omg.",
    "org.w3c.dom.",
    "org.xml.sax.")

  def isPlatformClass(clazz: JavaClass): Boolean = {
    // all classes loaded by bootstrap loader are considered
    // platform classes. In addition, the older name based filtering
    // is also done for compatibility.
    if (clazz.isBootstrap)
      return true

    val uncleanedName: String = clazz.name
    val name = if(uncleanedName.startsWith("[")) {
      val bracketsRemoved = uncleanedName.dropWhile(_ == '[')
      if(bracketsRemoved.charAt(0) != 'L')
        return true; // base array type

      bracketsRemoved.drop(1)
    } else
      uncleanedName // nothing to do

    platformPrefixes.exists(name.startsWith)
  }
}