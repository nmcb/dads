/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package tests

import dads.v1.util.Resources

object InitCassandra {


  def main(args: Array[String]): Unit = {

    println(Resources.load("/cassandra/initialise.cql"))
  }

}
