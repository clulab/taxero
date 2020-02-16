package org.clulab.taxero

import scala.collection.mutable.HashMap
import org.clulab.processors.Processor
import org.clulab.processors.fastnlp.FastNLPProcessor

class Consolidator(
  val processor: Processor,
  private val counts: HashMap[Seq[String], Int],
  private val display: HashMap[Seq[String], Seq[String]],
) {

  def this(processor: Processor) = this(processor, HashMap.empty, HashMap.empty)

  def this() = this(new FastNLPProcessor)

  def add(tokens: Seq[String]): Unit = add(tokens, 1)

  def add(tokens: Seq[String], count: Int): Unit = {
    val lemmas = lemmatize(tokens)
    counts(lemmas) = counts.getOrElse(lemmas, 0) + count
    display.getOrElseUpdate(lemmas, tokens)
  }

  def getMatches: Seq[Match] = {
    counts
      .toIterator
      .map { case (lemmas, count) => Match(display(lemmas), count) }
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
