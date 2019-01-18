package monika.server.script

/**
  * - tag interface
  * - indicates a script can only be run by the user if they have root access
  * - all scripts can be run internally at any time
  */
trait RequireRoot { self: Script =>

}
