package org.clulab.taxero

import scala.collection.mutable

class Counter {

  val counts = mutable.HashMap.empty[Seq[String], Int]

  def keys = counts.keys

  def add(key: Seq[String]): Unit = add(key, 1)

  def add(key: Seq[String], count: Int): Unit = {
    counts(key) = counts.getOrElse(key, 0) + count
  }

  def getMatches: Seq[Match] = {
    counts
      .toIterator
      .map { case (m, c) => Match(m, c) }
      .toSeq
  }

}
