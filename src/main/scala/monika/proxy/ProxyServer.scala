package monika.proxy

import java.io.File

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http._
import monika.server.Constants
import net.lightbody.bmp.mitm.manager.ImpersonatingMitmManager
import net.lightbody.bmp.mitm.{KeyStoreFileCertificateSource, RootCertificateGenerator}
import org.littleshoot.proxy.impl.DefaultHttpProxyServer
import org.littleshoot.proxy.{HttpFilters, HttpFiltersAdapter, HttpFiltersSourceAdapter, HttpProxyServer}

object ProxyServer {

  /**
    * configures the behaviour of the HTTP/HTTPS proxy, which all requests of the profile user must pass through
    * @param transparent whether the proxy should not perform filtering at all
    *                    if this is set to true, allow/reject properties will be ignored
    *                    in addition, no certificate will be required
    * @param allowHtmlPrefix which text/html responses should be allowed through if the url starts with a prefix
    * @param rejectHtmlKeywords which text/html responses should be rejected if they contain one of the keywords
    */
  case class ProxySettings(transparent: Boolean, allowHtmlPrefix: Vector[String], rejectHtmlKeywords: Vector[String])

  private var server: HttpProxyServer = _

  def startOrRestart(settings: ProxySettings): Unit = {
    server.synchronized {
      stop()
      if (settings.transparent) startTransparent()
      else startWithFilter(settings)
    }
  }

  private def stop(): Unit = {
    server.synchronized {
      if (server != null) {
        server.stop()
        server = null
      }
    }
  }

  def makeCertificates(): Unit = {
    val rootCertificateGenerator = RootCertificateGenerator.builder.build

    // save the newly-generated Root Certificate and Private Key -- the .cer file can be imported
    // directly into a browser
    rootCertificateGenerator.saveRootCertificateAsPemFile(new File(Constants.Locations.Certificate))
    rootCertificateGenerator.savePrivateKeyAsPemFile(new File(Constants.Locations.PrivateKey), "123456")

    // or save the certificate and private key as a PKCS12 keystore, for later use
    rootCertificateGenerator.saveRootCertificateAndKey("PKCS12", new File(Constants.Locations.KeyStore),
      "private-key", "123456")
  }

  private def startTransparent(): Unit = {
    server.synchronized {
      assert(server == null)
      server = DefaultHttpProxyServer.bootstrap()
        .withPort(8085)
        .withAllowLocalOnly(true)
        .withTransparent(true)
        .start()
    }
  }

  private def startWithFilter(settings: ProxySettings): Unit = {
    def shouldAllow(request: HttpRequest, response: HttpResponse): Boolean = {
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
      val url = host + request.getUri
      settings.allowHtmlPrefix.exists(str => url.startsWith(str))
    }

    val filtersSource = new HttpFiltersSourceAdapter() {
      override def filterRequest(request: HttpRequest, ctx: ChannelHandlerContext): HttpFilters = {
        new HttpFiltersAdapter(request) {
          override def serverToProxyResponse(httpObject: HttpObject): HttpObject = {
            httpObject match {
              case http: HttpResponse =>
                if (shouldAllow(request, http)) httpObject
                else new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN)
              case _ => httpObject // allow all non-http responses
            }
          }
        }
      }
    }

    server.synchronized {
      assert(server == null)
      // https://github.com/lightbody/browsermob-proxy/tree/master/mitm
      // https://github.com/ganskef/LittleProxy-mitm
      val source = new KeyStoreFileCertificateSource("PKCS12", new File(Constants.Locations.KeyStore), "private-key", "123456")
      val mitmManager = ImpersonatingMitmManager.builder.rootCertificateSource(source).build
      DefaultHttpProxyServer.bootstrap()
        .withPort(8085)
        .withAllowLocalOnly(true)
        .withManInTheMiddle(mitmManager)
        .withFiltersSource(filtersSource)
        .start()
    }
  }

}
