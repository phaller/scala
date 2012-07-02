import scala.util.continuations._

class Return {

  def shifting: Int @cps[Any] = shift { k => k(5) }
  
  def p(i: Int): Int @cps[Any] = {
    val v = shifting + 3
    
    return v
  }
  
  def caller() = reset {
    println("calling println")
    println(p(3))
  }
}

object Test extends App {
  val r = new Return
  r.caller()
}
