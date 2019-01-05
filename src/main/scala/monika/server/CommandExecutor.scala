package monika.server

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File}

import monika.Primitives._
import org.apache.commons.exec.{CommandLine, DefaultExecutor, ExecuteException, PumpStreamHandler}
import org.slf4j.LoggerFactory
import scalaz.{@@, Tag}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object CommandExecutor {

  private val LOGGER = LoggerFactory.getLogger(getClass)

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
    LOGGER.debug(s"run: $program ${args.mkString(" ")}")
    val cmd = new CommandLine(program)
    cmd.addArguments(args)
    cmd.toString

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

  /**
    * - checks whether a program can be located within PATH by name
    * - it must exists as a file and monika must have exec permissions
    */
  def findProgramLocation(program: String @@ FileName): Option[String @@ FilePath] = {
    val programName = Tag.unwrap(program)
    Constants.PathList
      .map(path => new File(path + File.separator + programName))
      .find(file => file.exists && file.isFile && file.canExecute)
      .map(file => FilePath(file.getCanonicalPath))
  }

  def checkIfProgramsAreExecutable(): Unit = {
    val programs = Constants.ProfilePrograms ++ Constants.CallablePrograms.asList
    val cannotExecute = programs.filter(findProgramLocation(_).isEmpty)
    for (program <- cannotExecute) {
      val programName = Tag.unwrap(program)
      LOGGER.warn(s"cannot find executable program: $programName")
    }
  }

  // potential issues:
  // https://issues.apache.org/jira/browse/EXEC-54

}
