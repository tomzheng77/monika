package monika.server.script.locknext

import java.time.{LocalDate, ZoneOffset}

import monika.server.script.Script

object MakePasswords extends Script {

  private val Dictionary = ('A' to 'Z') ++ ('a' to 'z') ++ ('0' to '9')
  private val PasswordLength = 16
  private val PasswordsToGenerate = 100

  override def run(args: Vector[String]): SC[Unit] = SC(api => {
    val now = api.nowTime()
    val today = now.toLocalDate
    val seed = now.toInstant(ZoneOffset.UTC).toEpochMilli.hashCode()
    val nextPassword: R[String] = nextString(PasswordLength)(Dictionary)
    val nextPasswords: R[Map[LocalDate, String]] = {
      rands(PasswordsToGenerate)(nextPassword).map(pwds => {
        pwds.zipWithIndex.map(pair => today.plusDays(pair._2) -> pair._1).toMap
      })
    }
    val pwds: Map[LocalDate, String] = nextPasswords.apply(seed)._2
    update(state => state.copy(passwords = pwds))(api)
  })

}
