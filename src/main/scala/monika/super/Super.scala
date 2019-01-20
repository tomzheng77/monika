package monika.`super`

import java.io._
import java.net.{ServerSocket, Socket}

import monika.server.Constants
import org.apache.commons.exec.{CommandLine, DefaultExecutor, ExecuteException, PumpStreamHandler}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object Super {

  def startSocketServer(): Unit = {
    val listener = new ServerSocket(9002)
    while (true) {
      val socket = listener.accept()
      val input = socket.getInputStream
      val output = socket.getOutputStream

      val reader = new BufferedReader(new InputStreamReader(input))
      val command = reader.readLine()
      val args = command.split(' ')

      val cmd = new CommandLine(args.head)
      cmd.addArguments(args.tail)

      val executor = new DefaultExecutor()
      val stderr = new ByteArrayOutputStream()
      val psh = new PumpStreamHandler(output, stderr, input)
      executor.setStreamHandler(psh)

      val environment = Map("PATH" -> Constants.Path).asJava
      val exitValue = Try(executor.execute(cmd, environment)) match {
        case Success(value) => value
        case Failure(ex: ExecuteException) => ex.getExitValue
        case Failure(ex) => throw new RuntimeException(ex)
      }
    }
  }

  def main(args: Array[String]): Unit = {
    startSocketServer()
    val socket = new Socket("127.0.0.1", 9002)
    val input = socket.getInputStream
    val output = socket.getOutputStream

    val writer = new PrintWriter(new OutputStreamWriter(output))
    writer.write(args.mkString(" "))

    val handler = new PumpStreamHandler(System.out, System.err, System.in)
    handler.setProcessOutputStream(input)
    handler.setProcessInputStream(output)
    handler.start()
  }
}
