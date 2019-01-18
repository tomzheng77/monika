package monika.server.proxy

import io.netty.handler.codec.http.{HttpRequest, HttpResponse}

trait Filter {

  def shouldAllow(request: HttpRequest, response: HttpResponse): Boolean

}
