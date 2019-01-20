package monika.`super`

object Super {
  def main(args: Array[String]): Unit = {
    val builder = new ProcessBuilder()
    builder.command(args: _*)
    builder.inheritIO()

    val process = builder.start()
    val exitCode = process.waitFor()
    System.exit(exitCode)
  }
}
