package monika.server.proxy

import java.io.File

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http._
import monika.server.Constants.{Locations, ProxyPort}
import monika.server.{Constants, UseLogger}
import net.lightbody.bmp.mitm.manager.ImpersonatingMitmManager
import net.lightbody.bmp.mitm.{KeyStoreFileCertificateSource, RootCertificateGenerator}
import org.apache.commons.io.FileUtils
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
object ProxyServer extends UseLogger {

  private var server: HttpProxyServer = _

  def startOrRestart(filter: Filter): Unit = {
    this.synchronized {
      LOGGER.info(s"restarting proxy server")
      stop()
      filter match {
        case TransparentFilter => serveTransparently()
        case _ => serveWithFilter(filter)
      }
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

    FileUtils.writeStringToFile(new File(Locations.Readme),
      """IntelliJ IDEA - Proxy: Automatic
        |
        |https://drissamri.be/blog/2017/02/22/java-keystore-keytool-essentials/
        |cd /etc/pki/ca-trust/extracted/java/cacerts
        |keytool -importcert \
        |        -trustcacerts -file /home/tomzheng/monika/certs/certificate.cer \
        |        -alias monika \
        |        -keystore cacerts
        |
      """.stripMargin, Constants.GlobalEncoding)
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

  private def serveWithFilter(filter: Filter): Unit = {
    def allowOrRejectHttp(request: HttpRequest, response: HttpResponse): HttpResponse = {
      if (filter.shouldAllow(request, response)) response
      else new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN)
    }
    val filterObj: HttpFiltersSource = new HttpFiltersSourceAdapter() {
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
        .withFiltersSource(filterObj)
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
