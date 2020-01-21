package org.clulab.taxero

import java.io.File
import java.util.concurrent.Executors
import scala.io.Source
import scala.concurrent._
import duration._
import com.typesafe.scalalogging.LazyLogging
import org.apache.lucene.search._
import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.common.StringUtils._
import ai.lum.common.DisplayUtils._
import ai.lum.common.TryWithResources.using
import ai.lum.odinson._
import ai.lum.odinson.lucene.search._

object GenerateDistantSupervisionData extends App with LazyLogging {

  val config = ConfigFactory.load()
  val outdir = config[File]("taxero.datagen.outdir")
  val pairs = config[File]("taxero.datagen.pairs")
  val documentIdField = config[String]("odinson.index.documentIdField")
  val sentenceIdField = config[String]("odinson.index.sentenceIdField")

  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))

  logger.debug("starting extractor engine")

  val extractorEngine = ExtractorEngine.fromConfig

  def mkQuery(lhs: String, rhs: String) = {
    val lhsPattern = lhs
      .split(" ")
      .map(t => "\"" + t.escapeJava + "\"")
      .mkString(" ")
    val rhsPattern = rhs
      .split(" ")
      .map(t => "\"" + t.escapeJava + "\"")
      .mkString(" ")
    val lhsQuery = extractorEngine.compiler.mkQuery(lhsPattern)
    val rhsQuery = extractorEngine.compiler.mkQuery(rhsPattern)
    new OdinsonFilteredQuery(lhsQuery, rhsQuery)
  }

  using (Source.fromFile(pairs)) { src =>
    val futures = Future.traverse(src.getLines) { line =>
      Future {
        val Array(hypo, hyperCandidate, isHyper) = line.split("\t")
        val outfile = new File(outdir, s"$hypo/$hyperCandidate.tsv")
        logger.debug(s"searching for ${hypo.display} and ${hyperCandidate.display}")
        val query = mkQuery(hypo, hyperCandidate)
        val results = extractorEngine.query(query)
        logger.debug(s"${results.totalHits.display} sentences found for ${hypo.display} and ${hyperCandidate.display}")
        for (scoreDoc <- results.scoreDocs) {
          val doc = extractorEngine.doc(scoreDoc.doc)
          val docId = doc.get(documentIdField)
          val sentId = doc.get(sentenceIdField)
          outfile.writeString(s"$docId\t$sentId\n", append = true)
        }
        ()
      }
    }
    logger.debug("waiting for all futures")
    Await.result(futures, Duration.Inf)
  }

}
