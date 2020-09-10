package org.clulab.taxero

import scala.io.Source
import scala.collection.mutable
import ai.lum.common.StringUtils._
import ai.lum.common.ConfigUtils._
import ai.lum.common.ConfigFactory
import ai.lum.common.TryWithResources.using
import ai.lum.odinson._
import ai.lum.odinson.lucene.search._
import ai.lum.odinson.utils.QueryUtils
import org.clulab.processors._
import org.clulab.processors.fastnlp.FastNLPProcessor


case class MatchWithEvidence(
  tokens: Seq[String],
  docId: String,
  sentenceId: Int,
)


object TaxonomyReader {
  def fromConfig: TaxonomyReader = {
    val config = ConfigFactory.load()
    val extractorEngine = ExtractorEngine.fromConfig
    val proc = new FastNLPProcessor
    new TaxonomyReader(extractorEngine, proc)
  }
}

class TaxonomyReader(
  val extractorEngine: ExtractorEngine,
  val proc: Processor,
) {


  def getHypernyms(tokens: Seq[String]): Seq[MatchWithEvidence] = {
    val lemmas = convertToLemmas(tokens)
    val extractors = mkHypernymExtractors(lemmas)
    getMatches(extractors)
  }

  def convertToLemmas(words: Seq[String]): Seq[String] = {
    val s = words.mkString(" ")
    val doc = proc.mkDocument(s)
    proc.tagPartsOfSpeech(doc)
    proc.lemmatize(doc)
    doc.clear()
    val sentence = doc.sentences.head
    // return the lemmas
    sentence.lemmas.get
  }

  def mkHypernymExtractors(lemmas: Seq[String]): Seq[Extractor] = {
    mkExtractorsFromFile(lemmas, "hypernym-rules.yml")
  }

  /** gets the contents of a rule file, as well as a tokenized query
   *  and returns the corresponding odinson extractors
   */
  def mkExtractorsFromFile(lemmas: Seq[String], rulefile: String): Seq[Extractor] = {
    using (Source.fromResource(rulefile)) { rules =>
      mkExtractorsFromRules(lemmas, rules.mkString)
    }
  }

  def mkExtractorsFromRules(lemmas: Seq[String], rules: String): Seq[Extractor] = {
    val variables = Map("query" -> mkLemmaPattern(lemmas))
    extractorEngine.ruleReader.compileRuleFile(rules.mkString, variables)
  }

  // tokens changed to lemmas as the first argument  
  def mkLemmaPattern(lemmas: Seq[String]): String = {            
    //println("TOKENS: " + tokens.mkString(" "))
    println("LEMMAS: " + lemmas.mkString(" "))
    lemmas
      .map(x => s"[lemma=${QueryUtils.maybeQuoteLabel(x)}]")
      .mkString(" ")
    // for "other" "dogs" ---> ["other", "dog']
    // [lemma=other] [lemma=dog]
  }

  def getMatches(extractors: Seq[Extractor]): Seq[MatchWithEvidence] = {
    for (m <- extractorEngine.extractMentions(extractors))
      yield MatchWithEvidence(getResult(m), m.docId, m.sentenceId.toInt)
  }

  def getResult(mention: Mention): Seq[String] = {
    val args = mention.arguments
    val m = args.get("result") match {
      // if there is a captured mention called "result" then that's the result
      case Some(mentions) => mentions.head.odinsonMatch
      // if there is no named result, then use the whole match as the result
      case None => mention.odinsonMatch
    }
    extractorEngine.getTokens(mention.luceneDocId, m)
  }



}
