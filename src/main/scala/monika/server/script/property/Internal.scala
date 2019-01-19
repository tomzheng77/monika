package monika.server.script.property

import monika.server.script.Script

/**
  * - tag interface
  * - indicates a script can only be run from the internal queue
  * - script cannot be called directly
  */
trait Internal { self: Script =>

}
