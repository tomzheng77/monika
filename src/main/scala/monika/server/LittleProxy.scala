package monika.server

import java.io.File

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http._
import monika.server.Constants.{Locations, ProxyPort}
import net.lightbody.bmp.mitm.manager.ImpersonatingMitmManager
import net.lightbody.bmp.mitm.{KeyStoreFileCertificateSource, RootCertificateGenerator}
import org.littleshoot.proxy._
import org.littleshoot.proxy.impl.DefaultHttpProxyServer

/**
  * - can provide transparent proxy (no additional certificate required) which does not
  *   perform any filter. a transparent proxy should have no problem maintaining long TCP connections
  *   which are required by online games
  *
  * - can provide MITM proxy, which can filter based on the URL of the requested page as well
  *   as the content of the page. an SSL certificate must be imported to use this proxy
  */
object LittleProxy extends UseLogger {

  /**
    * configures the behaviour of the HTTP/HTTPS proxy, which all requests of the profile user must pass through
    * @param transparent whether the proxy should not perform filtering at all
    *                    if this is set to true, allow/reject properties will be ignored
    *                    in addition, no certificate will be required
    * @param allowHtmlPrefix which text/html responses should be allowed through if the url starts with a prefix
    * @param rejectHtmlKeywords which text/html responses should be rejected if they contain one of the keywords
    */
  case class ProxySettings(
    transparent: Boolean = true,
    allowHtmlPrefix: Vector[String] = Vector.empty,
    rejectHtmlKeywords: Vector[String] = Vector.empty
  )

  val Transparent = ProxySettings(transparent = true)

  private var server: HttpProxyServer = _

  def startOrRestart(settings: ProxySettings): Unit = {
    this.synchronized {
      LOGGER.info(s"restarting proxy server (transparent: ${settings.transparent}")
      stop()
      if (settings.transparent) serveTransparently()
      else serveWithFilter(settings)
      LOGGER.info("proxy server successfully started")
    }
  }

  private def stop(): Unit = {
    this.synchronized {
      if (server != null) {
        LOGGER.info("stopping proxy server")
        server.stop()
        server = null
        LOGGER.info("proxy server successfully stopped")
      }
    }
  }

  def writeCertificatesToFiles(): Unit = {
    LOGGER.info(s"writing certificates to directory ${Locations.CertificateRoot}")

    // create the certificate root if it does not exist
    val certificateRoot = new File(Locations.CertificateRoot)
    certificateRoot.mkdirs()

    val rootCertificateGenerator = RootCertificateGenerator.builder.build

    // save the newly-generated Root Certificate and Private Key -- the .cer file can be imported
    // directly into a browser
    rootCertificateGenerator.saveRootCertificateAsPemFile(new File(Locations.Certificate))
    rootCertificateGenerator.savePrivateKeyAsPemFile(new File(Locations.PrivateKey), "123456")

    // or save the certificate and private key as a PKCS12 keystore, for later use
    rootCertificateGenerator.saveRootCertificateAndKey("PKCS12", new File(Locations.KeyStore),
      "private-key", "123456")
  }

  private def serveTransparently(): Unit = {
    this.synchronized {
      assert(server == null)
      server = DefaultHttpProxyServer.bootstrap()
        .withPort(ProxyPort)
        .withAllowLocalOnly(true)
        .withTransparent(true)
        .start()
    }
  }

  private def serveWithFilter(settings: ProxySettings): Unit = {
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
      val uri = request.getUri
      val url = if (uri.startsWith("http://") || uri.startsWith("https://")) uri else host + uri
      settings.allowHtmlPrefix.exists(str => url.startsWith(str))
    }

    def allowOrRejectHttp(request: HttpRequest, response: HttpResponse): HttpResponse = {
      if (shouldAllow(request, response)) response
      else new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN)
    }

    val filter: HttpFiltersSource = new HttpFiltersSourceAdapter() {
      override def filterRequest(request: HttpRequest, ctx: ChannelHandlerContext): HttpFilters = {
        new HttpFiltersAdapter(request) {
          override def serverToProxyResponse(httpObject: HttpObject): HttpObject = {
            httpObject match {
              case response: HttpResponse => allowOrRejectHttp(request, response)
              case _ => httpObject // allow all non-http responses
            }
          }
        }
      }
    }

    def startProxyWithKeyStore(file: File): Unit = {
      val source = new KeyStoreFileCertificateSource("PKCS12", file, "private-key", "123456")
      val mitmManager = ImpersonatingMitmManager.builder.rootCertificateSource(source).build
      server = DefaultHttpProxyServer.bootstrap()
        .withPort(ProxyPort)
        .withAllowLocalOnly(true)
        .withManInTheMiddle(mitmManager)
        .withFiltersSource(filter)
        .start()
    }

    this.synchronized {
      assert(server == null)
      // https://github.com/lightbody/browsermob-proxy/tree/master/mitm
      // https://github.com/ganskef/LittleProxy-mitm
      val keyStoreFile = new File(Locations.KeyStore)
      if (!keyStoreFile.exists()) LOGGER.error(s"keystore file (${Locations.KeyStore}) does not exist")
      else if (!keyStoreFile.canRead) LOGGER.error(s"keystore file (${Locations.KeyStore}) is not readable")
      else startProxyWithKeyStore(keyStoreFile)
    }
  }

}
