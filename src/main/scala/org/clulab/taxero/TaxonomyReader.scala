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
import org.clulab.processors.fastnlp.FastNLPProcessor

case class Match(
  result: Seq[String],
  count: Int,
)

case class ScoredMatch(
  query: Seq[String],
  result: Seq[String],
  count: Int,
  score: Double,
)

object TaxonomyReader {
  def fromConfig: TaxonomyReader = {
    val config = ConfigFactory.load()
    val extractorEngine = ExtractorEngine.fromConfig
    val wordEmbeddings = new Word2Vec(config[String]("taxero.wordEmbeddings"))
    new TaxonomyReader(extractorEngine, wordEmbeddings)
  }
}

class TaxonomyReader(
  val extractorEngine: ExtractorEngine,
  val wordEmbeddings: Word2Vec,
) {

  lazy val proc = new FastNLPProcessor

  def getHypernyms(tokens: Seq[String]): Seq[Match] = {
    getMatches(tokens, mkHypernymQueries)
  }

  def getRankedHypernyms(tokens: Seq[String]): Seq[ScoredMatch] = {
    rankMatches(tokens, getHypernyms(tokens))
  }

  def getHyponyms(tokens: Seq[String]): Seq[Match] = {
    getMatches(tokens, mkHyponymQueries)
  }

  def getRankedHyponyms(tokens: Seq[String]): Seq[ScoredMatch] = {
    rankMatches(tokens, getHyponyms(tokens))
  }

  def getCohyponyms(tokens: Seq[String]): Seq[Match] = {
    getMatches(tokens, mkCohyponymQueries)
  }

  def getRankedCohyponyms(tokens: Seq[String]): Seq[ScoredMatch] = {
    rankMatches(tokens, getCohyponyms(tokens))
  }

  def getExpandedHypernyms(pattern: Seq[String], n: Int): Seq[ScoredMatch] = {
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

  def executeGivenRule(tokens: Seq[String], rule: String): Seq[ScoredMatch] = {
    val query = mkGivenQuery(tokens, rule)
    rankMatches(tokens, getMatches(Seq(query)))
  }

  def getMatches(tokens: Seq[String], mkQueries: Seq[String] => Seq[OdinsonQuery]): Seq[Match] = {
    println("querying: " + tokens.mkString(" "))
    for {
      m <- getMatches(mkQueries(tokens))
      if m.result != tokens
    } yield m
  }

  def getMatches(queries: Seq[OdinsonQuery]): Seq[Match] = {
    // get matches
    val matches = for {
      query <- queries
      results = extractorEngine.query(query)
      scoreDoc <- results.scoreDocs
      odinsonMatch <- scoreDoc.matches
      mention = if (odinsonMatch.namedCaptures.nonEmpty) odinsonMatch.namedCaptures.head.capturedMatch else odinsonMatch
      result = extractorEngine.getTokens(scoreDoc.doc, mention)
    } yield result.toSeq
    // count matches and return them
    val counter = new Counter
    matches.foreach(counter.add)
    counter.getMatches
  }

  def rankMatches(query: Seq[String], matches: Seq[Match]): Seq[ScoredMatch] = {
    matches
      .map(m => scoreMatch(query, m))
      .sortBy(-_.count)
  }

  def scoreMatch(query: Seq[String], m: Match): ScoredMatch = {
    ScoredMatch(query, m.result, m.count, similarityScore(query, m.result))
  }

  def mkEmbedding(tokens: Seq[String]): Array[Double] = {
    wordEmbeddings.makeCompositeVector(tokens)
  }

  def getHead(tokens: Seq[String]): Seq[String] = {
    Seq(tokens.last)
  }

  def similarityScore(query: Seq[String], result: Seq[String], freq: Double = 1): Double = {
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

  def mkPattern(tokens: Seq[String]): String = {
    tokens.map(t => "\"" + t.escapeJava + "\"").mkString(" ")
  }

  def mkLemmaPattern(tokens: Seq[String]): String = {
    // lemmatize
    val lemmas = convertToLemmas(tokens)
    println("TOKENS: " + tokens.mkString(" "))
    println("LEMMAS: " + lemmas.mkString(" "))
    lemmas.map(t => "[lemma=\"" + t.escapeJava + "\"]").mkString(" ")
    // for "other" "dogs" ---> ["other", "dog']
    // [lemma=other] [lemma=dog]
  }

  def mkHypernymQueries(tokens: Seq[String]): Seq[OdinsonQuery] = {
    mkQueries(tokens, "hypernym-rules.txt")
  }

  def mkHyponymQueries(tokens: Seq[String]): Seq[OdinsonQuery] = {
    mkQueries(tokens, "hyponym-rules.txt")
  }

  def mkCohyponymQueries(tokens: Seq[String]): Seq[OdinsonQuery] = {
    mkQueries(tokens, "cohyponym-rules.txt")
  }

  def mkGivenQuery(tokens: Seq[String], rule: String): OdinsonQuery = {
    val variables = Map(
      "query" -> mkPattern(tokens),
      "chunk" -> "( [tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)? )",
    )
    val formatted = rule.replaceVariables(variables)
    extractorEngine.compiler.compile(formatted)
  }

  def mkQueries(tokens: Seq[String], rulefile: String): Seq[OdinsonQuery] = {
    using (Source.fromResource(rulefile)) { rules =>
      val variables = Map(
        "query" -> mkLemmaPattern(tokens),
        "chunk" -> "( [tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)? )",
      )
      rules.mkString
        .replaceVariables(variables)
        .split("""\s*\n\s*\n\s*""")
        .filter(line => !line.startsWith("#"))
        .map{ r => println(r); r }
        .map(extractorEngine.compiler.compile)
    }
  }

  // --------------------------------------------

  def convertToLemmas(words: Seq[String]): Seq[String] = {
    val s = words.mkString(" ")
    val doc = proc.annotate(s)
    val sentence = doc.sentences.head
    // return the lemmas
    sentence.lemmas.get
  }

}
