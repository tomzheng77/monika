package monika.server.script.property

/**
  * - tag interface
  * - indicates a script can only be run from the internal queue
  * - script cannot be called directly
  */
case object Internal extends Property
