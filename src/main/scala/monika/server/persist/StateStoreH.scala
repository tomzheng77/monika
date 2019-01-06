package monika.server.persist

import monika.server.pure.Model.MonikaState

/**
  * - persists a single MonikaState to disk
  * - provides a method to perform a stateful transaction on MonikaState
  * - reports any errors to log
  */
trait StateStoreH {

  /**
    * - the caller can provide a function which modifies the state and returns some value R
    * - this is atomic, if an error occurs then no changes will be persisted
    * - no two transactions can occur at once, one must wait for the other
    */
  def transaction[R](fn: MonikaState => (MonikaState, R)): R

}
