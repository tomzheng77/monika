package monika.server.proxy
import io.netty.handler.codec.http.{HttpMessage, HttpRequest, HttpResponse}
import monika.server.UseLogger

case class HTMLPrefixFilter(allow: Set[String]) extends Filter with UseLogger {
  override def shouldAllow(request: HttpRequest, response: HttpResponse): Boolean = {
    def mkHeaders(msg: HttpMessage): Map[String, String] = {
      import scala.collection.JavaConverters._
      msg.headers().asScala.map(entry => entry.getKey -> entry.getValue).toMap
    }
    val requestHeaders = mkHeaders(request)
    if (!requestHeaders.contains("Host")) return false // block all requests without host specified

    // filter only if the response is of type text/html
    // i.e. images, audio, JS, CSS will not be filtered
    // this will emulate filtering based on the URL bar
    val responseHeaders = mkHeaders(response)
    val contentType = responseHeaders.getOrElse("Content-Type", "")
    if (!contentType.startsWith("text/html")) return true

    // github.com
    // github.com/netty/netty/issues/2185
    val host = requestHeaders("Host")
    val uri = request.getUri
    val url = if (uri.startsWith("http://") || uri.startsWith("https://")) uri else host + uri
    if (allow.exists(str => url.startsWith(str))) true
    else {
      LOGGER.debug(s"intercepted request to $url"); false
    }
  }
}
