package monika.server

import monika.server.pure.Model.Profile

object UserControl {

  /**
    * sets the active user to the restrictions of the given profile
    * @param profile programs, projects, and proxy settings
    */
  def applyRestrictions(user: String, profile: Profile): Unit = {
//    def restrictProjects(projects)
//    profile.projects
  }

}
