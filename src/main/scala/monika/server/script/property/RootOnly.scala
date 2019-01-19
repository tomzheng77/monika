package monika.server.script.property

/**
  * - tag interface
  * - indicates a script can only be run by the user if they have root access
  * - all scripts can be run internally at any time
  */
case object RootOnly extends Property
