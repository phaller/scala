class Return {

  def p(i: Int): Int = {
    val l = List(1, 2, 8)
    val l2 = l.map { v =>
      if (v > 5)
        return v
      v * 2
    }
    l2(0)
  }
  
  def caller() = {
    println(p(3))
  }
}

object Test extends App {
  val r = new Return
  r.caller()
}
