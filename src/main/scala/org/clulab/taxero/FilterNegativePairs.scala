package org.clulab.taxero
import scala.io.Source
import scala.collection.mutable
import ai.lum.common.StringUtils._
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.common.ConfigFactory
import ai.lum.common.TryWithResources.using
import ai.lum.odinson._
import ai.lum.odinson.lucene.search._
import org.clulab.embeddings.word2vec.Word2Vec
import java.io.File
import org.apache.lucene.search._
import org.clulab.embeddings.word2vec.Word2Vec

object FilterNegativePairs extends App{
  val config = ConfigFactory.load()
  val reader = TaxonomyReader.fromConfig
  println("started reading the code...")

  val negfilenamepath: String = config[String]("neg.files.path")
  val negfilteredfile = config[File]("neg.filtered.output")
  val file = new File(negfilenamepath)
  println("reading the directory")
  val files = file.listFiles().filter(_.isFile)
  println("looped through directory")

  for (f <- files) {
    val fileName = f.getName
    println(fileName)

    for (line <- Source.fromFile(f).getLines()) {
      val tokens = line.trim.split("\t")
      val hypo = tokens(0).trim.split(" ")
      val hyper = tokens(1).trim.split(" ")
      val cos_sim = reader.similarityScore(hypo, hyper)
      //negfilteredfile.writeString(s"$line\t$cos_sim\n", append = true)
      if (cos_sim > 0.25) {
        negfilteredfile.writeString(s"$line\t$cos_sim\n", append = true)
      }
    }
  }
}
