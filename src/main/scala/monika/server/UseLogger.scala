package monika.server

import org.slf4j.{Logger, LoggerFactory}

trait UseLogger {

  protected val LOGGER: Logger = LoggerFactory.getLogger(getClass)

}
