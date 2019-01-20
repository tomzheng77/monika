package monika.server.proxy

import io.netty.handler.codec.http.{FullHttpResponse, HttpRequest, HttpResponse}
import monika.server.{Constants, UseLogger}

case class KeywordRejectFilter(keywords: Set[String]) extends Filter with UseLogger {
  override def shouldAllow(request: HttpRequest, response: HttpResponse): Boolean = {
    response match {
      case full: FullHttpResponse => {
        val content = full.content().toString(Constants.GlobalCharset)
        !keywords.exists(content.contains)
      }
      case _ => true
    }
  }
}
