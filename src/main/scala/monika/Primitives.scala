package monika

import scalaz.{@@, Tag}

object Primitives {

  /**
    * - the canonical path of a file or folder
    * - by definition, a canonical path is both absolute and unique
    */
  sealed trait FilePath
  def FilePath[A](a: A): A @@ FilePath = Tag[A, FilePath](a)

  /**
    * - the name of a file or folder relative to it's parent
    */
  sealed trait FileName
  def FileName[A](a: A): A @@ FileName = Tag[A, FileName](a)

}
