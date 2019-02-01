package monika.server.script.locknext

import monika.server.script.Script

object ListPasswords extends Script {

  override def run(args: Vector[String]): IOS[Unit] = IOS(api => {
    val state = api.getState()
    val today = api.nowTime().toLocalDate
    for (i <- 0 until 100) {
      val date = today.plusDays(i)
      for (password <- state.passwords.get(date)) {
        val (a, b) = password.splitAt(password.length / 2)
        api.printLine(date.toString)
        api.printLine(a)
        api.printLine("")
        api.printLine(date.toString)
        api.printLine(b)
        api.printLine("")
      }
    }
  })

}
