package org.clulab.taxero

import java.io.File
import scala.io.Source
import ai.lum.common.TryWithResources.using
import ai.lum.common.FileUtils._

object ConllTask extends App {

  val reader = TaxonomyReader.fromConfig
  val parent = new File("/home/marco/data/taxero")
  val queries: Array[Array[String]] = using (Source.fromFile("queries.txt")) {
    src => src.getLines.map(_.split(" ")).toArray
  }

  for (query <- queries) {
    // directory to store results for this particular hyponym
    val dir = new File(parent, normalize(query))
    // ensure directory exists
    dir.mkdirs()
    // iterate over found hypernyms
    for (m <- reader.getHypernyms(query)) {
      val file = new File(dir, normalize(m.tokens) + ".tsv")
      val data = s"${m.docId}\t${m.sentenceId}\n"
      file.writeString(data, append = true)
    }
  }

  def normalize(tokens: Seq[String]): String = tokens.mkString(" ")

}
