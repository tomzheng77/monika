package monika

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import org.apache.commons.exec.{CommandLine, DefaultExecutor, PumpStreamHandler}

object Environment {

  case class CommandOutput(exitValue: Int, stdout: Array[Byte], stderr: Array[Byte])

  def call(program: String, args: Array[String] = Array.empty, input: Array[Byte] = Array.empty): CommandOutput = {
    val cmd = new CommandLine(program)
    cmd.addArguments(args)

    val executor = new DefaultExecutor()
    val stdin = new ByteArrayInputStream(input)
    val stdout = new ByteArrayOutputStream()
    val stderr = new ByteArrayOutputStream()
    val psh = new PumpStreamHandler(stdout, stderr, stdin)
    executor.setStreamHandler(psh)

    val exitValue = executor.execute(cmd)
    CommandOutput(exitValue, stdout.toByteArray, stderr.toByteArray)
  }

}
