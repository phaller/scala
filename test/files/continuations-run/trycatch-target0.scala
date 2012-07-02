  class Return extends scala.AnyRef {

    def p(i: Int): Int = {
      val nonLocalReturnKey1: Object = new Object();
      try {
        val tmp1: Int = 5;
        val v: Int = tmp1.+(3);
        throw new scala.runtime.NonLocalReturnControl[Int](nonLocalReturnKey1, v)
      } catch {
        case (ex @ (_: scala.runtime.NonLocalReturnControl[Int])) => if (ex.key.eq(nonLocalReturnKey1))
          ex.value
        else
          throw ex
      }
    };

    def caller(): Any = {
      val tmp2: Int = Return.this.p(3);
      Predef.println(tmp2)
    }

  };

  object Test extends App {
    val r: Return = new Return();
    r.caller()
  }
