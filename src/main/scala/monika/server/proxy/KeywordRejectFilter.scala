package monika.server.proxy

import io.netty.handler.codec.http.{FullHttpResponse, HttpMessage, HttpRequest, HttpResponse}
import monika.server.{Constants, UseLogger}

case class KeywordRejectFilter(keywords: Set[String]) extends Filter with UseLogger {

  override def shouldAllow(request: HttpRequest, response: HttpResponse): Boolean = {
    // reject if keyword found in header
    val url = getURL(request)
    val content = getContent(response)
    val regexes = keywords.map(_.r)
    !regexes.exists(r => r.findFirstMatchIn("").nonEmpty)
  }

  private def getContent(response: HttpResponse): Option[String] = {
    val responseHeaders = mkHeaders(response)
    val contentType = responseHeaders.getOrElse("Content-Type", "")
    if (!contentType.startsWith("text/html")) return None
    response match {
      case full: FullHttpResponse => Some(full.content().toString(Constants.GlobalCharset))
      case _ => None
    }
  }

  private def getURL(request: HttpRequest): String = {
    val requestHeaders = mkHeaders(request)
    val host = requestHeaders.getOrElse("Host", "")
    val uri = request.getUri
    val url = if (uri.startsWith("http://") || uri.startsWith("https://")) uri else host + uri
    url.replaceFirst("^https?://", "")
  }

  private def mkHeaders(msg: HttpMessage): Map[String, String] = {
    import scala.collection.JavaConverters._
    msg.headers().asScala.map(entry => entry.getKey -> entry.getValue).toMap
  }

}
