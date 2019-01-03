package monika

import java.time.{LocalDateTime, ZoneOffset}

import monika.Interpreter.{NIL, RWS, ST}
import org.json4s.JsonAST.JValue
import org.json4s.{DefaultFormats, Formats}
import scalaz.{@@, ReaderWriterState, Tag}

object Profile {

  /**
    * configures the behaviour of the HTTP/HTTPS proxy, which all requests of the profile user must pass through
    * @param transparent whether the proxy should not perform filtering at all
    *                    if this is set to true, allow/reject properties will be ignored
    *                    in addition, no certificate will be required
    * @param allowHtmlPrefix which text/html responses should be allowed through if the url starts with a prefix
    * @param rejectHtmlKeywords which text/html responses should be rejected if they contain one of the keywords
    */
  case class ProxySettings(transparent: Boolean, allowHtmlPrefix: Vector[String], rejectHtmlKeywords: Vector[String])

  /**
    * a bookmark to display on the browser's toolbar
    * @param name name to display, if not provided should be a shortened url
    * @param url url it should lead to
    */
  case class Bookmark(name: String, url: String)

  /**
    * defines which programs, projects and websites the profile user can use
    * when this mode is active
    *
    * @param name name of this mode
    * @param programs names of programs allowed when this mode is active
    * @param projects names of projects allowed when this mode is active
    * @param bookmarks bookmarks to display inside the browser for convenience
    * @param proxy restricts which websites can be accessed
    */
  case class ProfileMode(name: String, programs: Vector[String @@ FileName], projects: Vector[String @@ FileName],
                         bookmarks: Vector[Bookmark], proxy: ProxySettings)

  /**
    * @param startTime the start time of this profile
    * @param endTime the end time of this profile
    * @param profile which profile should be used throughout the duration
    */
  case class ProfileInQueue(startTime: LocalDateTime, endTime: LocalDateTime, profile: ProfileMode)

  /**
    * constructs a profile from a .json definition file
    * this is not a deserialization process, it is fault tolerant and provides
    * default values for all fields
    */
  def constructProfile(definition: JValue, defaultName: String): ProfileMode = {
    implicit val formats: Formats = DefaultFormats
    ProfileMode(
      (definition \ "name").extractOpt[String].getOrElse(defaultName),
      (definition \ "programs").extractOpt[Vector[String]].getOrElse(Vector.empty).map(FileName),
      (definition \ "projects").extractOpt[Vector[String]].getOrElse(Vector.empty).map(FileName),
      (definition \ "bookmarks").extractOpt[Vector[JValue]].getOrElse(Vector.empty).map(v => {
        val url = (v \ "url").extractOpt[String].getOrElse("http://www.google.com")
        val re = "[A-Za-z-]+(\\.[A-Za-z-]+)*\\.[A-Za-z-]+".r
        val name = (v \ "name").extractOpt[String].orElse(re.findFirstIn(url)).getOrElse("Unknown")
        Bookmark(name, url)
      }),
      ProxySettings(
        (definition \ "proxy" \ "transparent").extractOpt[Boolean].getOrElse(false),
        (definition \ "proxy" \ "allowHtmlPrefix").extractOpt[Vector[String]].getOrElse(Vector.empty),
        (definition \ "proxy" \ "rejectHtmlKeywords").extractOpt[Vector[String]].getOrElse(Vector.empty)
      )
    )
  }

  /**
    * the full runtime state of the Monika program
    * since it has very limited amounts of state (< 10KB), it is completely feasible
    * to represent it with an immutable data structure
    *
    * the profile modes are fully stored within the state, hence prevents them
    * from being modified even if the .json files are changed
    *
    * invariant: the queue is sorted by start time
    * invariant: no two queue items overlap in time
    * invariant: profiles are mapped by their name
    */
  case class MonikaState(queue: Vector[ProfileInQueue], at: Option[ProfileInQueue], profiles: Map[String, ProfileMode]) {
    assert(queue.sortBy(i => i.startTime.toEpochSecond(ZoneOffset.UTC)) == queue)
    private val intervals = queue.map(i => {
      (i.startTime.toEpochSecond(ZoneOffset.UTC), i.endTime.toEpochSecond(ZoneOffset.UTC))
    })
    assert(intervals.forall(pair => pair._2 > pair._1))
    assert(intervals.indices.dropRight(1).forall(i => intervals(i)._2 <= intervals(i + 1)._1))
    assert(profiles.forall(pair => pair._1 == pair._2.name))
  }

  /**
    * ensures: any items past the given time inside the queue are dropped
    */
  def dropOverdueItems(): ST[Unit] = RWS((ext, state) => {
    (NIL, Unit, state.copy(queue = state.queue.dropWhile(item => item.endTime.isBefore(ext.nowTime))))
  })

  /**
    * requires: the queue is not empty
    * ensures: the first item of the queue is returned
    * ensures: the first item is removed from the queue
    */
  def popQueue(): ST[ProfileInQueue] = RWS((_, state) => {
    (NIL, state.queue.head, state.copy(queue = state.queue.tail))
  })

  /**
    * represents the external view
    * @param projects known projects mapped from name to path
    */
  case class External(
    nowTime: LocalDateTime,
    projects: Map[String @@ FileName, String @@ FilePath]
  )

  /**
    * ensures: the state is returned
    */
  def readExtAndState(): ST[(External, MonikaState)] = RWS((ext, state) => {
    (NIL, (ext, state), state)
  })

  /**
    * an absolute or canonical path pointing to a file or folder
    */
  sealed trait FilePath
  def FilePath[A](a: A): A @@ FilePath = Tag[A, FilePath](a)

  /**
    * the name of a file or folder
    */
  sealed trait FileName
  def FileName[A](a: A): A @@ FileName = Tag[A, FileName](a)

  sealed trait Effect
  case class RunCommand(program: String, args: Vector[String] = NIL) extends Effect
  case class RestartProxy(settings: ProxySettings) extends Effect
  case class WriteStringToFile(path: String @@ FilePath, content: String) extends Effect

  type ST[T] = scalaz.ReaderWriterState[External, Vector[Effect], MonikaState, T]
  type STR[T] = (Vector[Effect], T, MonikaState)
  def RWS[T](f: (External, MonikaState) => (Vector[Effect], T, MonikaState)) = ReaderWriterState.apply[External, Vector[Effect], MonikaState, T](f)
  val NIL = Vector.empty

}
