package monika.editor

import javax.swing.{JFrame, WindowConstants}
import org.gjt.sp.jedit.jEdit.JEditPropertyManager
import org.gjt.sp.jedit.textarea.StandaloneTextArea

object Editor {

  def main(args: Array[String]): Unit = {
    val frame = new JFrame()
    val area = new StandaloneTextArea(new JEditPropertyManager())
    frame.setContentPane(area)
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
    frame.setVisible(true)
  }

}
