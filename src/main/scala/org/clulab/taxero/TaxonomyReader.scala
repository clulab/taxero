package org.clulab.taxero

import scala.io.Source
import scala.collection.mutable
import ai.lum.odinson._
import ai.lum.odinson.lucene.search._
import ai.lum.common.StringUtils._
import ai.lum.common.ConfigUtils._
import ai.lum.common.ConfigFactory
import ai.lum.common.TryWithResources.using
import org.clulab.embeddings.word2vec.Word2Vec

case class Match(
  result: Array[String],
  count: Int,
)

case class ScoredMatch(
  query: Array[String],
  result: Array[String],
  score: Double,
)

class Counter {
  val counts = mutable.HashMap.empty[Array[String], Int]
  def keys = counts.keys
  def add(key: Array[String]): Unit = add(key, 1)
  def add(key: Array[String], count: Int): Unit = {
    counts(key) = counts.getOrElse(key, 0) + count
  }
  def getMatches: Array[Match] = {
    counts
      .toIterator
      .map { case (m, c) => Match(m, c) }
      .toArray
  }
}

object TaxonomyReader {
  def fromConfig: TaxonomyReader = {
    val config = ConfigFactory.load()
    val extractorEngine = ExtractorEngine.fromConfig
    val wordEmbeddings = new Word2Vec(config[String]("taxero.wordEmbeddings"))
    val contextEmbeddings = new Word2Vec(config[String]("taxero.contextEmbeddings"))
    new TaxonomyReader(extractorEngine, wordEmbeddings, contextEmbeddings)
  }
}

class TaxonomyReader(
  val extractorEngine: ExtractorEngine,
  val wordEmbeddings: Word2Vec,
  val contextEmbeddings: Word2Vec,
) {

  def getHypernyms(tokens: Array[String]): Array[Match] = {
    getMatches(mkHypernymQueries(tokens))
  }

  def getRankedHypernyms(tokens: Array[String]): Array[ScoredMatch] = {
    rankMatches(tokens, getHypernyms(tokens))
  }

  def getHyponyms(tokens: Array[String]): Array[Match] = {
    getMatches(mkHyponymQueries(tokens))
  }

  def getRankedHyponyms(tokens: Array[String]): Array[ScoredMatch] = {
    rankMatches(tokens, getHyponyms(tokens))
  }

  def getCohyponyms(tokens: Array[String]): Array[Match] = {
    getMatches(mkCohyponymQueries(tokens))
  }

  def getRankedCohyponyms(tokens: Array[String]): Array[ScoredMatch] = {
    rankMatches(tokens, getCohyponyms(tokens))
  }

  def getExpandedHypernyms(pattern: Array[String], n: Int): Array[ScoredMatch] = {
    // start query set with the provided query
    val allQueries = mutable.HashSet(pattern)
    // add the n closest cohyponyms to the query set
    allQueries ++= getRankedCohyponyms(pattern).take(n).map(_.result)
    // start an empty map for the hypernym candidate counts
    val hypernymCounts = new Counter
    // count hypernym candidates
    for {
      q <- allQueries
      m <- getHypernyms(q)
    } hypernymCounts.add(m.result, m.count)
    // add the heads of each hypernym to the results
    for (candidate <- hypernymCounts.keys) {
      hypernymCounts.add(getHead(candidate))
    }
    // add the head of the original pattern to the results
    hypernymCounts.add(getHead(pattern))
    // return scored hypernyms
    rankMatches(pattern, hypernymCounts.getMatches)
  }

  def getMatches(queries: Array[OdinsonQuery]): Array[Match] = {
    // get matches
    val matches = for {
      query <- queries
      results = extractorEngine.query(query)
      scoreDoc <- results.scoreDocs
      odinsonMatch <- scoreDoc.matches
      result = extractorEngine.getTokens(odinsonMatch)
    } yield result
    // count matches and return them
    val counter = new Counter
    matches.foreach(counter.add)
    counter.getMatches
  }

  def rankMatches(query: Array[String], matches: Array[Match]): Array[ScoredMatch] = {
    matches
      .map(m => scoreMatch(query, m))
      .sortBy(-_.score)
  }

  def scoreMatch(query: Array[String], m: Match): ScoredMatch = {
    ScoredMatch(query, m.result, similarityScore(query, m.result, m.count))
  }

  def mkEmbedding(tokens: Array[String]): Array[Double] = {
    wordEmbeddings.makeCompositeVector(tokens)
  }

  def getHead(tokens: Array[String]): Array[String] = {
    Array(tokens.last)
  }

  def similarityScore(query: Array[String], result: Array[String], freq: Double = 1): Double = {
    // 2. get embedding for MWEs
    //    a. embedding of head word
    //    b. average of all word embeddings
    //    c. weighted average (more weight to the head)
    //    d. robert's model
    // 3. frequency * cosineSimilarity(emb(query), emb(result))
    val q = mkEmbedding(query)
    val r = mkEmbedding(result)
    freq * Word2Vec.dotProduct(q, r)
  }

  def mkPattern(tokens: Array[String]): String = {
    tokens.map(t => "\"" + t.escapeJava + "\"").mkString(" ")
  }

  def mkHypernymQueries(tokens: Array[String]): Array[OdinsonQuery] = {
    mkQueries(tokens, "hypernym-rules.txt")
  }

  def mkHyponymQueries(tokens: Array[String]): Array[OdinsonQuery] = {
    mkQueries(tokens, "hyponym-rules.txt")
  }

  def mkCohyponymQueries(tokens: Array[String]): Array[OdinsonQuery] = {
    mkQueries(tokens, "cohyponym-rules.txt")
  }

  def mkQueries(tokens: Array[String], rulefile: String): Array[OdinsonQuery] = {
    using (Source.fromResource(rulefile)) { rules =>
      val pattern = mkPattern(tokens)
      val variables = Map("pattern" -> pattern)
      rules.mkString
        .replaceVariables(variables)
        .split("""\s*\n\s*\n\s*""")
        .map(extractorEngine.compiler.compile)
    }
  }

}
