package monika.server.subprocess

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File}

import monika.Primitives.{FileName, FilePath}
import monika.server.subprocess.Commands.Command
import monika.server.{Constants, UseLogger}
import org.apache.commons.exec.{CommandLine, DefaultExecutor, ExecuteException, PumpStreamHandler}
import scalaz.{@@, Tag}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object Subprocess extends UseLogger {

  case class CommandOutput(exitValue: Int, stdout: Array[Byte], stderr: Array[Byte])

  /**
    * calls a program either by name inside PATH or the full path
    * blocks until execution is complete
    * captures and returns all program output
    *
    * @param program either the name of the program or the full path
    * @param args to execute the program with
    * @param input to pass into stdin
    * @param workingDirectory directory where the program will be run
    * @return an object containing exit value, stdout and stderr
    */
  def callUnsafe(program: String, args: Array[String] = Array.empty, input: Array[Byte] = Array.emptyByteArray,
                 workingDirectory: Option[String @@ FilePath] = None): CommandOutput = {
    // resolve the program within customized PATH (incl. Constants.PathAdd)
    val resolvedProgram: String = {
      if (program.startsWith("/")) program
      else findExecutableInPath(FileName(program)).map(Tag.unwrap).headOption.getOrElse {
        throw new RuntimeException(s"cannot resolve program '$program' in PATH")
      }
    }

    LOGGER.debug(s"run: $program ($resolvedProgram) ${args.mkString(" ")}")
    val cmd = new CommandLine(resolvedProgram)
    cmd.addArguments(args)

    val executor = new DefaultExecutor()
    val stdin = new ByteArrayInputStream(input)
    val stdout = new ByteArrayOutputStream()
    val stderr = new ByteArrayOutputStream()
    val psh = new PumpStreamHandler(stdout, stderr, stdin)
    executor.setStreamHandler(psh)
    workingDirectory.map(Tag.unwrap).map(new File(_)).foreach(executor.setWorkingDirectory)

    val environment = Map("PATH" -> Constants.Path).asJava
    val exitValue = Try(executor.execute(cmd, environment)) match {
      case Success(value) => value
      case Failure(ex: ExecuteException) => ex.getExitValue
      case Failure(ex) => throw new RuntimeException(ex)
    }
    CommandOutput(exitValue, stdout.toByteArray, stderr.toByteArray)
  }

  /**
    * - checks whether a program can be located within PATH by name
    * - it must exists as a file and monika must have exec permissions
    */
  def findExecutableInPath(program: String @@ FileName): Vector[String @@ FilePath] = {
    val programName = Tag.unwrap(program)
    Constants.PathList
      .map(path => new File(path + File.separator + programName))
      .filter(file => file.exists && file.isFile && file.canExecute)
      .map(file => FilePath(file.getCanonicalPath))
  }

  // potential issues:
  // https://issues.apache.org/jira/browse/EXEC-54

}
