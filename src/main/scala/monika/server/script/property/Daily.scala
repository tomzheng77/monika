package monika.server.script.property

import java.time.LocalTime

// the script should be run daily at the specified time
case class Daily(time: LocalTime) extends Property
