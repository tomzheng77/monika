package monika.adm

import java.io._
import java.net.{ServerSocket, Socket}

import monika.server.Constants
import org.apache.commons.exec.{CommandLine, DefaultExecutor, ExecuteException, PumpStreamHandler}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object Admin {

  def startSocketServer(): Unit = {
    val listener = new ServerSocket(9002)
    new Thread(() => {
      while (true) {
        val socket = listener.accept()
        val input = socket.getInputStream
        val output = socket.getOutputStream

        val reader = new DataInputStream(input)
        val argc = reader.readInt()
        val argv = Array.fill(argc)(reader.readUTF())

        val cmd = new CommandLine(argv.head)
        cmd.addArguments(argv.tail)

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
        println(stderr.size())
      }
    }).start()
  }

  def useSTTYRaw(): Unit = {
    val cmd = Array("/bin/sh", "-c", "stty raw </dev/tty")
    Runtime.getRuntime.exec(cmd)
  }

  def main(args: Array[String]): Unit = {
    useSTTYRaw()
    startSocketServer()
    val socket = new Socket("127.0.0.1", 9002)
    val input = socket.getInputStream
    val output = socket.getOutputStream

    val writer = new DataOutputStream(output)
    writer.writeInt(args.length)
    args.foreach(writer.writeUTF)

    val handler = new PumpStreamHandler(System.out, System.err, System.in)
    handler.setProcessOutputStream(input)
    handler.setProcessInputStream(output)
    handler.start()
  }
}
