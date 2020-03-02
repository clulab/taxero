package org.clulab.taxero

import java.io.File
import ai.lum.common.FileUtils._
import scala.io.Source
import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._


object GenerateSampleRuleRankingEval extends App{

  val config = ConfigFactory.load()
  val evalfilenamepath: String = config[String]("eval.files.path")
  val sampleRuleRankingfile = config [File] ("sample.rule.ranking.file")

  sampleRuleRankingfile.writeString(s"Query\tHypernym\tCount\tCosine_similarity\tFinal_score\n", append = true)
  val file = new File (evalfilenamepath)
  val files = file.listFiles().filter(_.isFile)

  for (f <- files) {
    val ruleName = f.getName
    var i: Int = 0
    for (line <- Source.fromFile(f).getLines()) {
      if (i < 100) {
        sampleRuleRankingfile.writeString(s"$ruleName\t$line\n", append = true)
        i += 1
      }
    }

  }

}
