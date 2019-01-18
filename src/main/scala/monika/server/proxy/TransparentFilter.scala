package monika.server.proxy
import io.netty.handler.codec.http.{HttpRequest, HttpResponse}

case object TransparentFilter extends Filter {
  override def shouldAllow(request: HttpRequest, response: HttpResponse): Boolean = true
}
