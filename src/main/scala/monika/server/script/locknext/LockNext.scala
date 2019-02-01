package monika.server.script.locknext

import java.time.LocalDate

import monika.server.Constants
import monika.server.script.Script
import monika.server.script.property.RootOnly
import monika.server.subprocess.Commands.passwd

object LockNext extends Script(RootOnly) {

  def popPasswordFor(date: LocalDate): IOS[Option[String]] = IOS(api => {
    val state = api.getState()
    if (!state.passwords.contains(date)) None
    else {
      api.setState(state.copy(passwords = state.passwords - date))
      state.passwords.get(date)
    }
  })

  override def run(args: Vector[String]): IOS[Unit] = for {
    tomorrow  ← nowTime().map(_.toLocalDate.plusDays(1))
    passwdOpt ← popPasswordFor(date = tomorrow)
    _ <- passwdOpt match {
      case None => printLine(s"there is no password for $tomorrow")
      case Some(password) => steps[Unit](
        call(passwd, "-l", Constants.MonikaUser),
        call(passwd, "-u", Constants.UnlockerUser),
        callWithInput(passwd, Array(Constants.UnlockerUser, "--stdin"), password.getBytes(Constants.GlobalCharset)),
        printLine(s"the password has been set to $password")
      )
    }
  } yield Unit

}
