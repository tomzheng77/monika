package monika.server.script.internal

import monika.server.script.Script
import monika.server.script.property.Property

// indicates the script is part of the mainline
// must be one of:
// - brick
// - freedom
// - lock-profile
// - unlock
abstract class QScript(props: Property*) extends Script(props: _*) {

}
