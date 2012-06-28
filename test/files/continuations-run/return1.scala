import scala.util.continuations._

class Return {

  def shifting: Int @cps[Any] = shift { k => k(5) }
  
  def p(i: Int): Int @cps[Any] = {
    val v = shifting + 3
    return v
  }
  
  def caller(x: Int): Int = {
    reset {
      println(p(3))
    }
    if (x > 3)
      return 10
    8
  }  
}

object Test extends App {
  val r = new Return
  val n = r.caller(4)
}
