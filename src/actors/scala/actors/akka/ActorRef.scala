package scala.actors

import scala.util.Timeout
import java.util.concurrent.TimeoutException

trait ActorRef  {

/**
   * Sends a one-way asynchronous message. E.g. fire-and-forget semantics.
   * <p/>
   *
   * If invoked from within an actor then the actor reference is implicitly passed on as the implicit 'sender' argument.
   * <p/>
   *
   * This actor 'sender' reference is then available in the receiving actor in the 'sender' member variable,
   * if invoked from within an Actor. If not then no sender is available.
   * <pre>
   *   actor ! message
   * </pre>
   * <p/>
   */
  def !(message: Any)(implicit sender: ActorRef = null): Unit

  /**
   * Sends a message asynchronously, returning a future which may eventually hold the reply.
   * TODO (VJ)
   * The Future will be completed with an [[akka.actor.AskTimeoutException]] after the given
   * timeout has expired.
   *
   * <b>NOTE:</b>
   * Use this method with care. In most cases it is better to use '!' together with implicit or explicit
   * sender parameter to implement non-blocking request/response message exchanges.
   *
   * If you are sending messages using <code>ask</code> and using blocking operations on the Future, such as
   * 'get', then you <b>have to</b> use <code>getContext().sender().tell(...)</code>
   * in the target actor to send a reply message to the original sender, and thereby completing the Future,
   * otherwise the sender will block until the timeout expires.
   *
   * When using future callbacks, inside actors you need to carefully avoid closing over
   * the containing actor’s reference, i.e. do not call methods or access mutable state
   * on the enclosing actor from within the callback. This would break the actor
   * encapsulation and may introduce synchronization bugs and race conditions because
   * the callback will be scheduled concurrently to the enclosing actor. Unfortunately
   * there is not yet a way to detect these illegal accesses at compile time.
   */
  def ?(message: Any)(implicit timeout: Timeout): Future[Any]

  /**
   * Shuts down the actor its dispatcher and message queue.
   */
  def start(): ActorRef

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
  
  
  private[actors] def localActor: AbstractActor 
}


private[actors] class OutputChannelRef(val actor: OutputChannel[Any]) extends ActorRef {
  
  def ?(message: Any)(implicit timeout: Timeout): Future[Any] = 
    throw new UnsupportedOperationException("NIY")

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
  def !(message: Any)(implicit sender: ActorRef = null): Unit = 
    if (sender != null)
    	actor.send(message, sender.localActor)
    else 
        actor ! message
        
        
 /**
   * Shuts down the actor its dispatcher and message queue.
   */
  def stop(): Unit = ()
  
  override def equals(that: Any) = 
    that.isInstanceOf[OutputChannelRef] && that.asInstanceOf[OutputChannelRef].actor == this.actor
  
  private[actors] override def localActor: AbstractActor = 
    throw new UnsupportedOperationException("Output channel does not have an instance of the actor")
  
  def forward(message: Any): Unit = throw new UnsupportedOperationException("OutputChannel does not support forward.")
  
  def start(): ActorRef = throw new UnsupportedOperationException("OutputChannel does not support start.")
}

private[actors] class ReactorRef(override val actor: Reactor[Any]) extends OutputChannelRef(actor) {
       
  override def start(): ActorRef = {
    actor.start()
    this
  }  

  /**
   * Forwards the message and passes the original sender actor as the sender.
   * <p/>
   * Works with '!' and '?'.
   */
  override def forward(message: Any) = actor.forward(message)
    
}

private[actors] class ReplyActorRef(override val actor: InternalReplyReactor) extends ReactorRef(actor) {
  
  /**
   * Sends a message asynchronously, returning a future which may eventually hold the reply.
   */
  override def ?(message: Any)(implicit timeout: Timeout): Future[Any] = 
    Futures.future{
      val dur = if (timeout.duration.isFinite()) timeout.duration.toMillis else (java.lang.Long.MAX_VALUE >> 2)
      actor !?(dur, message) match {
        case Some(x) => x
        case None => new AskTimeoutException("? operation timed out.")
      }
    }

  override def start(): ActorRef = {
    actor.start()
    this
  }
    
}

private[actors] final class InternalActorRef(override val actor: InternalActor) extends ReplyActorRef(actor) {
  
  override def stop(): Unit = actor.stop('normal)
  
  private[actors] override def localActor: InternalActor = this.actor
}

/**
 * This is what is used to complete a Future that is returned from an ask/? call,
 * when it times out.
 */
class AskTimeoutException(message: String, cause: Throwable) extends TimeoutException {
  def this(message: String) = this(message, null: Throwable)
}
