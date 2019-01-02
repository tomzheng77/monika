package monika

object Firewall {

  def rejectOutgoingHttp(forUser: String): Unit = {
    Environment.call("iptables", s"-w 10 -A OUTPUT -p tcp -m owner --uid-owner $forUser --dport 80 -j REJECT".split(' '))
    Environment.call("iptables", s"-w 10 -A OUTPUT -p tcp -m owner --uid-owner $forUser --dport 443 -j REJECT".split(' '))
  }

}
