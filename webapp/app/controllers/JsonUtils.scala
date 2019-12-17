package controllers

import play.api.libs.json._
import org.clulab.taxero._

/** utilities to convert odin mentions into json objects
 *  that can be returned in http responses
 */
object JsonUtils {

  def mkJson(ms: Seq[ScoredMatch]): JsValue = {
    Json.arr(ms.map(mkJson): _*)
  }

  def mkJson(m: ScoredMatch): Json.JsValueWrapper = {
    Json.obj(
      "query"  -> m.query,
      "result" -> m.result,
      "count"  -> m.count,
      "score"  -> m.score,
    )
  }

}
