package monika.server

import java.io.File

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http._
import monika.server.Profile.ProxySettings
import net.lightbody.bmp.mitm.manager.ImpersonatingMitmManager
import net.lightbody.bmp.mitm.{KeyStoreFileCertificateSource, RootCertificateGenerator}
import org.littleshoot.proxy.impl.DefaultHttpProxyServer
import org.littleshoot.proxy.{HttpFilters, HttpFiltersAdapter, HttpFiltersSourceAdapter, HttpProxyServer}

object ProxyServer {

  private var server: HttpProxyServer = _

  def stop(): Unit = {
    server.synchronized {
      if (server != null) {
        server.stop()
        server = null
      }
    }
  }

  def start(settings: ProxySettings): Unit = {
    server.synchronized {
      stop()
      if (settings.transparent) startTransparent()
      else startWithFilter(settings)
    }
  }

  def makeCertificates(): Unit = {
    val rootCertificateGenerator = RootCertificateGenerator.builder.build

    // save the newly-generated Root Certificate and Private Key -- the .cer file can be imported
    // directly into a browser
    rootCertificateGenerator.saveRootCertificateAsPemFile(new File("/home/shared/proxy/certificate.cer"))
    rootCertificateGenerator.savePrivateKeyAsPemFile(new File("/home/shared/proxy/private-key.pem"), "123456")

    // or save the certificate and private key as a PKCS12 keystore, for later use
    rootCertificateGenerator.saveRootCertificateAndKey("PKCS12", new File("/home/shared/proxy/keystore.p12"),
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

      val responseHeaders = mkHeaders(response)
      val contentType = responseHeaders.getOrElse("Content-Type", "")
      if (!contentType.startsWith("text/html")) return true // allow all non-http responses

      // github.com
      // github.com/netty/netty/issues/2185
      val host = requestHeaders("Host")
      val url = host + request.getUri
      settings.allowHtmlPrefix.exists(str => url.startsWith(str))
    }
    server.synchronized {
      assert(server == null)
      // https://github.com/lightbody/browsermob-proxy/tree/master/mitm
      // https://github.com/ganskef/LittleProxy-mitm
      // Google Chrome -> Manage Certificates -> Authorities -> Import (certificate.cer), Trust *
      // create a CA Root Certificate using default settings
      // use a previously generated keystore.p12 file
      val source = new KeyStoreFileCertificateSource("PKCS12", new File("/home/shared/proxy/keystore.p12"), "private-key", "123456")
      val mitmManager = ImpersonatingMitmManager.builder.rootCertificateSource(source).build
      DefaultHttpProxyServer.bootstrap()
        .withPort(8085)
        .withAllowLocalOnly(true)
        .withManInTheMiddle(mitmManager)
        .withFiltersSource(new HttpFiltersSourceAdapter() {
          override def filterRequest(request: HttpRequest, ctx: ChannelHandlerContext): HttpFilters = {
            new HttpFiltersAdapter(request) {
              override def serverToProxyResponse(httpObject: HttpObject): HttpObject = {
                // filter only if the response is of type text/html
                // i.e. images, audio, JS, CSS will not be filtered
                // this will emulate filtering based on the URL bar
                httpObject match {
                  case http: HttpResponse =>
                    if (shouldAllow(request, http)) httpObject
                    else new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN)
                  case _ => httpObject // allow all non-http responses
                }
              }
            }
          }
        })
        .start()
    }
  }

}
