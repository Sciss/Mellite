package scala.swing

object LabelWrap {
  def apply(c: javax.swing.JLabel): Label =
    UIElement.cachedWrapper[Label](c)
}
