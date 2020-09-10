/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1.util

import java.io._

import scala.io._
import scala.util.control._

object Resources {

  def readFile(resource: String): String = {
    var src: BufferedSource = null
    var str: InputStream    = null
    try {
      str = Resources.getClass.getResourceAsStream(resource)
      src = Source.fromInputStream(str)
      src.getLines().mkString("\n")
    } catch {
      case NonFatal(_) => throw new FileNotFoundException(resource)
    } finally {
      str.close()
      src.close()
    }
  }
}
