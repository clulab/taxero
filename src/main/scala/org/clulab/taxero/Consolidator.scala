package org.clulab.taxero

import scala.collection.mutable.{ArrayBuffer, HashMap}
import org.clulab.processors.Processor
import org.clulab.processors.fastnlp.FastNLPProcessor

/** This class represents a Consolidator object that groups
 *  the results of a query by lemmas.
 */
class Consolidator(
  val processor: Processor,
  private val counts: HashMap[Seq[String], Int],
  private val display: HashMap[Seq[String], Seq[String]],
  private val evidence: HashMap[Seq[String], ArrayBuffer[Evidence]],
) {

  def this(processor: Processor) = this(processor, HashMap.empty, HashMap.empty, HashMap.empty)

  def this() = this(new FastNLPProcessor)

  def keys = display.values

  def add(tokens: Seq[String], sentences: Seq[Evidence] = Seq()): Unit = add(tokens, 1, sentences)

  def add(tokens: Seq[String], count: Int, sentences: Seq[Evidence]): Unit = {
    val lemmas = lemmatize(tokens)
    counts(lemmas) = counts.getOrElse(lemmas, 0) + count
    display.getOrElseUpdate(lemmas, tokens)
    evidence(lemmas) = evidence.getOrElse(lemmas, ArrayBuffer.empty)
    evidence(lemmas).appendAll(sentences)
  }

  /** Returns the results of the consolidation process */
  def getMatches: Seq[Match] = {
    counts
      .toIterator
      .map { case (lemmas, count) => Match(display(lemmas), count, evidence(lemmas)) }
      .toSeq
  }

  private def lemmatize(tokens: Seq[String]): Seq[String] = {
    val s = tokens.mkString(" ")
    val doc = processor.mkDocument(s)
    processor.tagPartsOfSpeech(doc)
    processor.lemmatize(doc)
    doc.clear()
    doc.sentences.head.lemmas.get
  }

}
