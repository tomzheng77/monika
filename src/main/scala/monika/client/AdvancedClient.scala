package monika.client

import org.apache.commons.exec.CommandLine

import scala.util.Try

object AdvancedClient {

  private val VariableNameRegex = "[a-zA-Z0-9_.]+"
  private val VariableReferenceRegex = "\\$\\{" + VariableNameRegex + "\\}"

  def main(args: Array[String]): Unit = {

  }

  case class ClientState(
    aliases: Map[String, List[String]],
    variables: Map[String, String],
    functions: Map[String, Vector[List[String]]]
  )

  private def expand(args: List[String])(state: ClientState): Vector[List[String]] = {
    val expandedArgs = args
      .flatMap(expandAlias(_)(state.aliases))
      .map(substituteVariable(_)(state.variables))

    expandedArgs match {
      case "call" :: function :: args ⇒ Vector(args)
      case otherwise ⇒ Vector(expandedArgs)
    }
  }

  private def substituteVariable(text: String)(variables: Map[String, String]): String = {
    val regex = VariableReferenceRegex.r
    val buffer = new StringBuilder()
    val a = regex.split(text).iterator
    val b = regex.findAllMatchIn(text).map(m ⇒ m.group(0))
    while (a.hasNext || b.hasNext) {
      if (a.hasNext) {
        val x = a.next()
        buffer.append(x)
      }
      if (b.hasNext) {
        val y = b.next()
        val yx = y.substring(2, y.length - 1)
        val yv = variables.get(yx).map(substituteVariable).getOrElse("")
        buffer.append(yv)
      }
    }
    buffer.toString()
  }

  private def expandAlias(token: String)(aliases: Map[String, List[String]]): List[String] = {
    aliases.get(token) match {
      case None ⇒ List(token)
      case Some(expand) ⇒ expand.flatMap(expandAlias(_)(aliases))
    }
  }

  private def parseCommand(line: String): List[String] = Try({
    val cmd: CommandLine = CommandLine.parse(line.trim)
    val seq = cmd.getExecutable :: cmd.getArguments.toList
    val notComment = seq.indexOf("#") match {
      case -1 ⇒ seq
      case index ⇒ seq.take(index)
    }
    notComment.map(s ⇒ {
      if (s.startsWith("\"") && s.endsWith("\""))
        s.substring(1, s.length - 1) else s
    })
  }).getOrElse(Nil)

}
