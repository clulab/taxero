
package org.clulab.taxero

import java.io.File
import scala.io.Source
import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._


object GenerateEvalFile extends App {
  println("it works up to here")
  val config = ConfigFactory.load()
  val reader = TaxonomyReader.fromConfig
  println("it doesn't work")
//  val filename = "eval_hypo_query_entities_unique.txt"
  val filename: String = config[String]("eval.entities")

  val outfile = config[File]("eval.output")

  for (query <- Source.fromFile(filename).getLines()) {
    val tokens = query.trim.split(" ")
    val matches = reader.getHypernyms(tokens)
    for (m <- matches) {
      val result = m.result.mkString(" ")
      outfile.writeString(s"$query\t$result\t${m.count}", append = true)
    }
  }

}
