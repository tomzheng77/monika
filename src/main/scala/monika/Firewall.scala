package monika

object Firewall {

  def rejectHttpFromProfile(): Unit = {
    Environment.call("iptables", s"-w 10 -A OUTPUT -p tcp -m owner --uid-owner ${Constants.ProfileUser} --dport 80 -j REJECT".split(' '))
    Environment.call("iptables", s"-w 10 -A OUTPUT -p tcp -m owner --uid-owner ${Constants.ProfileUser} --dport 443 -j REJECT".split(' '))
  }

}
