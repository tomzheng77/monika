package monika

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.{Timer, TimerTask}

import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.PumpStreamHandler
import org.apache.commons.exec.CommandLine

object Monika {

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

  def enableFirewallForProfile(): Unit = {
//    call("iptables", "-w 10 -A OUTPUT -p tcp -m owner --uid-owner profile --dport 80 -j REJECT".split(' '))
//    call("iptables", "-w 10 -A OUTPUT -p tcp -m owner --uid-owner profile --dport 443 -j REJECT".split(' '))
  }

  def onSecondTick(): Unit = {
    val out = call("ls", Array("-la"))
    println(new String(out.stdout, "UTF-8"))
  }

  def startCommandListener(): Unit = {

  }

  def startHttpProxyServer(): Unit = {

  }

  def main(args: Array[String]): Unit = {
    def scheduleOnSecondTick(): Unit = {
      val timer = new Timer()
      timer.schedule(new TimerTask {
        override def run(): Unit = onSecondTick()
      }, 0, 1000)
    }
    scheduleOnSecondTick()
    enableFirewallForProfile()
    startCommandListener()
    startHttpProxyServer()
  }

}
