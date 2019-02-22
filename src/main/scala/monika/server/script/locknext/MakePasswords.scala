package monika.server.script.locknext

import java.time.ZoneOffset

import monika.server.script.Script
import monika.server.script.property.RootOnly

object MakePasswords extends Script(RootOnly) {

  private val Dictionary = ('A' to 'Z') ++ ('a' to 'z') ++ ('0' to '9')
  private val PasswordLength = 16
  private val PasswordsToGenerate = 100

  override def run(args: Vector[String]): IOS[Unit] = for {
    time <- nowTime()
    _ <- transformState(state => state.copy(passwords = makePasswords(time)))
    _ <- printLine(s"the next $PasswordsToGenerate passwords have been generated")
  } yield Unit

  private def makePasswords(time: LocalDateTime): Map[LocalDate, String] = {
    val today = time.toLocalDate
    val seed = time.toInstant(ZoneOffset.UTC).toEpochMilli.hashCode()
    val nextPassword: R[String] = nextString(PasswordLength)(Dictionary)
    val nextPasswords: R[Map[LocalDate, String]] = {
      rands(PasswordsToGenerate)(nextPassword).map(pwds => {
        pwds.zipWithIndex.map(pair => today.plusDays(pair._2) -> pair._1).toMap
      })
    }
    nextPasswords.apply(seed)._2
  }

}
