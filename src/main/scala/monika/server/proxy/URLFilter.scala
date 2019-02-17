package monika.server.proxy
import io.netty.handler.codec.http.{HttpMessage, HttpRequest, HttpResponse}
import monika.server.UseLogger

import scala.util.matching.Regex

/**
  * - HTTP transactions will only be allowed if they pass both the allow [A] and reject [R] filters
  * - each filter uses a collection of matchers [M]
  * - [M]: a matcher is a predicate on a URL string, it can be prefix-based or regex based
  * - [A]: true if the response is non-HTTP, otherwise true iff request matches one or more matchers
  * - [R]: false iff request matches one or more matchers
  */
case class URLFilter(allow: Set[String], reject: Set[String]) extends Filter with UseLogger {

  private val allowM = CompositeMatcher(allow)
  private val rejectM = CompositeMatcher(reject)

  override def shouldAllow(request: HttpRequest, response: HttpResponse): Boolean = {
    val url = findURL(request)
    val contentType = findContentType(response)
    val pass: Boolean = passAllow(url, contentType) && passReject(url)
    if (pass) LOGGER.debug(s"[ALLOW] $url")
    else LOGGER.debug(s"[BLOCK] $url")
    pass
  }

  private def passAllow(url: String, contentType: String): Boolean = {
    !contentType.contains("text/html") || allowM.matches(url)
  }

  private def passReject(url: String): Boolean = {
    !rejectM.matches(url)
  }

  private def readHeaders(msg: HttpMessage): Map[String, String] = {
    import scala.collection.JavaConverters._
    msg.headers().asScala.map(entry => entry.getKey -> entry.getValue).toMap
  }

  private def findURL(request: HttpRequest): String = {
    // github.com/netty/netty/issues/2185
    val requestHeaders = readHeaders(request)
    val host = requestHeaders.getOrElse("Host", "")
    val uri = request.getUri
    val url = if (uri.startsWith("http://") || uri.startsWith("https://")) uri else host + uri
    url.replaceFirst("^https?://", "")
  }

  private def findContentType(response: HttpResponse): String = {
    val responseHeaders = readHeaders(response)
    responseHeaders.getOrElse("Content-Type", "")
  }

  private case class CompositeMatcher(set: Set[String]) {
    private val (regexes, prefixes) = set.partition(s => s.startsWith("/") && s.endsWith("/"))
    private val regexObjects: Set[Regex] = regexes.map(s => s.substring(1, s.length - 1).r)
    def matches(url: String): Boolean = {
      prefixes.exists(url.startsWith) || regexObjects.exists(_.findFirstMatchIn(url).nonEmpty)
    }
  }

}
