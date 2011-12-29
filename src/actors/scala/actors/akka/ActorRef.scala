package scala.actors

trait ActorRef {

  /**
   * Sends a message asynchronously, returning a future which may eventually hold the reply.
   */
  def ?(message: Any): Future[Any]

  /**
   * Sends a one-way asynchronous message. E.g. fire-and-forget semantics.
   * <p/>
   *
   * <p/>
   * <pre>
   *   actor ! message
   * </pre>
   * <p/>
   */
  def !(msg: Any): Unit

  def start(): ActorRef

  // TODO (VJ) test with linked
  /**
   * Shuts down the actor its dispatcher and message queue.
   */
  def stop(): Unit

  /**
   * Forwards the message and passes the original sender actor as the sender.
   * <p/>
   * Works with '!' and '?'.
   */
  def forward(message: Any)

  /**
   * Registers this actor to be a death monitor of the provided ActorRef
   * This means that this actor will get a Terminated()-message when the provided actor
   * is permanently terminated.
   *
   * @return the same ActorRef that is provided to it, to allow for cleaner invocations
   */
  def startsWatching(subject: ActorRef): ActorRef = throw new UnsupportedOperationException//TODO FIXME REMOVE THIS

  /**
   * Deregisters this actor from being a death monitor of the provided ActorRef
   * This means that this actor will not get a Terminated()-message when the provided actor
   * is permanently terminated.
   *
   * @return the same ActorRef that is provided to it, to allow for cleaner invocations
   */
  def stopsWatching(subject: ActorRef): ActorRef = throw new UnsupportedOperationException//TODO FIXME REMOVE THIS
  
}


private[actors] class ReactorRef(val actor: Reactor[Any]) extends ActorRef {

  /**
   * Sends a message asynchronously, returning a future which may eventually hold the reply.
   */
  def ?(message: Any): Future[Any] = throw new UnsupportedOperationException("NIY")// TODO (VJ) fix this when futures are introduced. Use broken promise by default.

  /**
   * Sends a one-way asynchronous message. E.g. fire-and-forget semantics.
   * <p/>
   *
   * <p/>
   * <pre>
   *   actor ! message
   * </pre>
   * <p/>
   */
  def !(msg: Any): Unit = actor ! msg

  def start(): ActorRef = this
  
  /**
   * Shuts down the actor its dispatcher and message queue.
   */
  def stop(): Unit = ()

  
  /**
   * Forwards the message and passes the original sender actor as the sender.
   * <p/>
   * Works with '!' and '?'.
   */
  def forward(message: Any) = actor.forward(message)

}

private[actors] class ReplyActorRef(override val actor: InternalReplyReactor) extends ReactorRef(actor) {
  
  /**
   * Sends a message asynchronously, returning a future which may eventually hold the reply.
   */
  override def ?(message: Any): Future[Any] = actor !! message

  override def start(): ActorRef = {
    actor.start()
    this
  }
  
}

private[actors] final class RichActorRef(override val actor: RichActor) extends ReplyActorRef(actor) {
  
  override def stop(): Unit = actor.stop('normal)
	
}


