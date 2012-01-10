package scala.actors

import scala.collection._

object ActorSystem {

  private[actors] val contextStack = new ThreadLocal[immutable.Stack[Boolean]] {
    override def initialValue() = immutable.Stack[Boolean]()
  }

  private[this] def withCleanContext(block: => ActorRef): ActorRef = {
    // push clean marker
    val old = contextStack.get
    contextStack.set(old.push(true))
    try {
      val instance = block

      if (instance eq null)
        throw new Exception("Actor instance passed to actorOf can't be 'null'")

      instance
    } finally {
      val stackAfter = contextStack.get
      if (stackAfter.nonEmpty)
        contextStack.set(if (!stackAfter.head) stackAfter.pop.pop else stackAfter.pop)
    }
  }
  
  def actorOf(factory: ⇒ InternalActor): ActorRef = withCleanContext {
    val r = new InternalActorRef(factory)
    r
  }  
  
  def actorOf[T <: InternalActor](implicit m: Manifest[T]): ActorRef = withCleanContext {
    val clazz = m.erasure.asInstanceOf[Class[_ <: RichActor]]
    val r = new InternalActorRef(clazz.newInstance())    
    r
  }
  
}