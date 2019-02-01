package monika.server.script.internal

import monika.server.proxy.TransparentFilter
import monika.server.script.Script
import monika.server.script.property.Internal

object Unlock extends Script(Internal) {

  override def run(args: Vector[String]): IOS[Unit] = steps(
    restartProxy(TransparentFilter),
    transformState(state => state.copy(filter = TransparentFilter, root = true)),
    clearAllRestrictions(),
    printLine("unlock success")
  )

}
