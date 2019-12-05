package org.clulab.taxero

import scala.collection.mutable
import ai.lum.odinson._
import ai.lum.odinson.lucene.search._
import ai.lum.common.StringUtils._

case class Match(
  result: String,
  count: Int,
)

case class ScoredMatch(
  query: String,
  result: String,
  score: Double,
)

class TaxonomyReader(
  val extractorEngine: ExtractorEngine,
) {

  def getHypernyms(pattern: String): Array[Match] = {
    getMatches(mkHypernymQueries(pattern))
  }

  def getRankedHypernyms(pattern: String): Array[ScoredMatch] = {
    rankMatches(pattern, getHypernyms(pattern))
  }

  def getHyponyms(pattern: String): Array[Match] = {
    getMatches(mkHyponymQueries(pattern))
  }

  def getRankedHyponyms(pattern: String): Array[ScoredMatch] = {
    rankMatches(pattern, getHyponyms(pattern))
  }

  def getCohyponyms(pattern: String): Array[Match] = {
    getMatches(mkCohyponymQueries(pattern))
  }

  def getRankedCohyponyms(pattern: String): Array[ScoredMatch] = {
    rankMatches(pattern, getCohyponyms(pattern))
  }

  def getExpandedHypernyms(pattern: String, n: Int): Array[ScoredMatch] = {
    // start query set with the provided query
    val allQueries = mutable.HashSet(pattern)
    // add the n closest cohyponyms to the query set
    allQueries ++= getRankedCohyponyms(pattern).take(n).map(_.result)
    // start an empty map for the hypernym candidate counts
    val hypernymCounts = mutable.HashMap.empty[String, Int]
    // count hypernym candidates
    for {
      q <- allQueries
      m <- getHypernyms(q)
    } hypernymCounts(m.result) = m.count + hypernymCounts.getOrElse(m.result, 0)
    // add the heads of each hypernym to the results
    for (hyper <- hypernymCounts.keys) {
      val head = getHead(hyper)
      hypernymCounts(head) = 1 + hypernymCounts.getOrElse(head, 0)
    }
    // add the head of the original pattern to the results
    val patternHead = getHead(pattern)
    hypernymCounts(patternHead) = 1 + hypernymCounts.getOrElse(patternHead, 0)
    // return scored hypernyms
    val allHypernyms = hypernymCounts
      .toIterator
      .map { case (candidate, count) => Match(candidate, count) }
      .toArray
    rankMatches(pattern, allHypernyms)
  }

  def getMatches(queries: Array[OdinsonQuery]): Array[Match] = {
    // get matches
    val matches = for {
      query <- queries
      results = extractorEngine.query(query)
      scoreDoc <- results.scoreDocs
      odinsonMatch <- scoreDoc.matches
      result = extractorEngine.getString(odinsonMatch)
    } yield result
    // count matches
    val counts = mutable.HashMap.empty[String, Int]
    for (m <- matches) {
      counts(m) = 1 + counts.getOrElse(m, 0)
    }
    // return counted matches
    counts
      .toIterator
      .map { case (m, c) => Match(m, c) }
      .toArray
  }

  def rankMatches(query: String, matches: Array[Match]): Array[ScoredMatch] = {
    matches
      .map(m => scoreMatch(query, m))
      .sortBy(-_.score)
  }

  def scoreMatch(query: String, m: Match): ScoredMatch = {
    ScoredMatch(query, m.result, similarityScore(query, m.result, m.count))
  }

  def getHead(query: String): String = {
    ???
  }

  def similarityScore(query: String, result: String, freq: Double = 1): Double = {
    ???
  }

  def mkHypernymQueries(pattern: String): Array[OdinsonQuery] = {
    ???
  }

  def mkHyponymQueries(pattern: String): Array[OdinsonQuery] = {
    ???
  }

  def mkCohyponymQueries(pattern: String): Array[OdinsonQuery] = {
    ???
  }

}
