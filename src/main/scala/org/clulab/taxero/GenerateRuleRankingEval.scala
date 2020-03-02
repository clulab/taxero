package org.clulab.taxero

import java.io.File
import scala.io.Source
import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._

object GenerateRuleRankingEval extends App{
  val config = ConfigFactory.load()
  val filename: String = config[String]("eval.files.path")
  val outfile1 = config[File]("rule.ranking.file1")
  val outfile2 = config[File]("rule.ranking.file2")
  println("getting started")
  for (line <- Source.fromFile(filename).getLines()) {
    println("already read the lines")
    val tokens = line.trim.split("\t")
    val countScore = tokens(2).toInt
    val cosineScore = tokens(3).toDouble
    val finalScore1 = scala.math.log1p(countScore) * cosineScore
    val finalScore2 = 1 - scala.math.pow ((1- cosineScore), countScore)
    outfile1.writeString(s"$line\t$finalScore1\n", append =true)
    outfile2.writeString(s"$line\t$finalScore2\n", append =true)

  }

}
