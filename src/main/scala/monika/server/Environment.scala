package monika.server

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File}

import monika.server.Model.FileName
import org.apache.commons.exec.{CommandLine, DefaultExecutor, ExecuteException, PumpStreamHandler}
import scalaz.{@@, Tag}
import scala.collection.JavaConverters._

import scala.util.{Failure, Success, Try}

object Environment {

  case class CommandOutput(exitValue: Int, stdout: Array[Byte], stderr: Array[Byte])

  /**
    * calls a program either by name inside PATH or the full path
    * blocks until execution is complete
    * captures and returns all program output
    *
    * @param program either the name of the program or the full path
    * @param args to execute the program with
    * @param input to pass into stdin
    * @return an object containing exit value, stdout and stderr
    */
  def call(program: String, args: Array[String] = Array.empty, input: Array[Byte] = Array.empty): CommandOutput = {
    val cmd = new CommandLine(program)
    cmd.addArguments(args)

    val executor = new DefaultExecutor()
    val stdin = new ByteArrayInputStream(input)
    val stdout = new ByteArrayOutputStream()
    val stderr = new ByteArrayOutputStream()
    val psh = new PumpStreamHandler(stdout, stderr, stdin)
    executor.setStreamHandler(psh)
    executor.setWorkingDirectory(new File(Constants.MonikaHome))

    val environment = Map("PATH" -> Constants.Path).asJava
    val exitValue = Try(executor.execute(cmd, environment)) match {
      case Success(value) => value
      case Failure(ex: ExecuteException) => ex.getExitValue
      case Failure(ex) => throw new RuntimeException(ex)
    }
    CommandOutput(exitValue, stdout.toByteArray, stderr.toByteArray)
  }

  def rejectOutgoingHttp(forUser: String): Unit = {
    call("iptables", s"-w 10 -A OUTPUT -p tcp -m owner --uid-owner $forUser --dport 80 -j REJECT".split(' '))
    call("iptables", s"-w 10 -A OUTPUT -p tcp -m owner --uid-owner $forUser --dport 443 -j REJECT".split(' '))
  }

  def canLocateProgram(program: String @@ FileName): Boolean = {
    val programName = Tag.unwrap(program)
    Constants.PathList.exists(aPath => {
      val file = new File(aPath + File.separator + programName)
      file.exists && file.isFile && file.canExecute
    })
  }

  def main(args: Array[String]): Unit = {
    println(canLocateProgram(FileName("ls")))
    println(canLocateProgram(FileName("lsa")))
  }

}
