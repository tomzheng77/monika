package monika.server.script.library

import scalaz.State

import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions

trait RandomOps {

  type R[A] = ST[Int, A]
  type ST[S, A] = State[S, A]
  def ST[S, A](f: S ⇒ (S, A)) = State(f)

  implicit def unit[S, A](a: A): State[S, A] = State.state(a)

  def sequence[S, A](fs: Seq[State[S, A]]): ST[S, Seq[A]] = State(initial ⇒ {
    val buffer = new ArrayBuffer[A](fs.length)
    var state: S = initial
    for (op ← fs) {
      val (newState, result) = op.apply(state)
      state = newState
      buffer.append(result)
    }
    (state, buffer.toVector)
  })

  def nextString(length: Int)(dictionary: Seq[Char]): R[String] = {
    intsNaturalLessThan(16)(dictionary.size).map(_.map(dictionary).mkString)
  }

  def nextBoolean: R[Boolean] = for {
    number <- nextInt
  } yield number > 0

  def nextInt: R[Int] = ST(rng => {
    val newSeed = nextSeed(rng)
    (newSeed, newSeed)
  })

  def nextLong: R[Long] = for {
    int0 <- nextInt
    int1 <- nextInt
  } yield int0 << 32 | int1

  def naturalInt: R[Int] = nextInt.map(math.abs)
  def naturalLessThan(n: Int): R[Int] =
    naturalInt.flatMap(i => {
      val mod = i % n
      if (i + (n - 1) - mod >= 0) unit(mod)
      else naturalLessThan(n)
    })

  def between(x: Int, y: Int): R[Int] =
    naturalLessThan(y - x).map(_ + x)

  def ints(count: Int): R[Seq[Int]] =
    ST(rng => sequence(List.fill(count)(nextInt))(rng))

  def intsNaturalLessThan(count: Int)(limit: Int): R[Seq[Int]] =
    ST(rng => sequence(List.fill(count)(
      naturalLessThan(limit)))(rng))

  def rands[T](count: Int)(rand: R[T]): R[Seq[T]] =
    ST(rng => sequence(List.fill(count)(rand))(rng))

  private def shift13Xor(x: Int): Int = x ^ x << 13
  private def shift17Xor(x: Int): Int = x ^ x >> 17
  private def shift5Xor(x: Int): Int = x ^ x << 5
  private def nextSeed(x: Int): Int = shift5Xor(shift17Xor(shift13Xor(x)))

}
