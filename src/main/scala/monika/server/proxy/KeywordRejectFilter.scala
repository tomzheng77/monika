package monika.server.proxy

import io.netty.handler.codec.http.{FullHttpResponse, HttpMessage, HttpRequest, HttpResponse}
import monika.server.{Constants, UseLogger}

case class KeywordRejectFilter(keywords: Set[String]) extends Filter with UseLogger {
  override def shouldAllow(request: HttpRequest, response: HttpResponse): Boolean = {
    def mkHeaders(msg: HttpMessage): Map[String, String] = {
      import scala.collection.JavaConverters._
      msg.headers().asScala.map(entry => entry.getKey -> entry.getValue).toMap
    }

    // reject if keyword found in header
    def matchAnyIn(string: String): Boolean = {

    }

    val requestHeaders = mkHeaders(request)
    val host = requestHeaders.getOrElse("Host", "")
    val uri = request.getUri
    val url = if (uri.startsWith("http://") || uri.startsWith("https://")) uri else host + uri
    val urlWithoutHTTP = url.replaceFirst("^https?://", "")
    if (keywords.exists(urlWithoutHTTP.contains)) false
    else response match {
      case full: FullHttpResponse => {
        val content = full.content().toString(Constants.GlobalCharset)
        !keywords.exists(content.contains)
      }
      case _ => true
    }
  }
}
