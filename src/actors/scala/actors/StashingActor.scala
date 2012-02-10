package scala.actors

import scala.collection._

object StashingActor extends Combinators {
  implicit def mkBody[A](body: => A) = new InternalActor.Body[A] {
    def andThen[B](other: => B): Unit = Actor.rawSelf.seq(body, other)
  }
}

@deprecated("Scala Actors are beeing removed from the standard library. Please refer to the migration guide.", "2.10")
trait StashingActor extends InternalActor {
  type Receive = PartialFunction[Any, Unit]

  // checks if StashingActor is created within the actorOf block
  creationCheck;

  private[actors] val ref = new InternalActorRef(this)
  
  val self: ActorRef = ref
  
  protected[this] val context: ActorContext = new ActorContext(ref)
  
  @volatile
  private[this] var myTimeout: Option[Long] = None

  def receiveTimeout: Option[Long] = myTimeout

  def receiveTimeout_=(timeout: Option[Long]) = {
    myTimeout = timeout
  }
  
  
  
  /**
   * Migration notes:
   *   this method replaces receiveWithin, receive and react methods from Scala Actors.
   */
  def receive: Receive

  /**
   * User overridable callback.
   * <p/>
   * Is called when an Actor is started by invoking 'actor'.
   */
  def preStart() {}

  /**
   * User overridable callback.
   * <p/>
   * Is called when 'actor.stop()' is invoked.
   */
  def postStop() {}

  /**
   * User overridable callback.
   * <p/>
   * Is called on a crashed Actor right BEFORE it is restarted to allow clean
   * up of resources before Actor is terminated.
   * By default it calls postStop()
   */
  def preRestart(reason: Throwable, message: Option[Any]) { postStop() }

  /**
   * Changes the Actor's behavior to become the new 'Receive' (PartialFunction[Any, Unit]) handler.
   * Puts the behavior on top of the hotswap stack.
   * If "discardOld" is true, an unbecome will be issued prior to pushing the new behavior to the stack
   */
  def become(behavior: Receive, discardOld: Boolean = true) {
    if (discardOld) unbecome()
    behaviorStack = behaviorStack.push(wrapWithSystemMessageHandling(behavior)) 
  }

  /**
   * Reverts the Actor behavior to the previous one in the hotswap stack.
   */
  def unbecome() {
    // never unbecome the initial behavior
    if (behaviorStack.size > 1)
      behaviorStack = behaviorStack.pop
  }

  /**
   * User overridable callback.
   * <p/>
   * Is called when a message isn't handled by the current behavior of the actor
   * by default it does: EventHandler.warning(self, message)
   */
  def unhandled(message: Any) {
    println("unhandeld")
    message match {
      case _ => throw new UnhandledMessageException(message, self)
    }
  }

  protected def sender: ActorRef = throw new UnsupportedOperationException("") // TODO (VJ) fix the output channel
  
  /*
   * Deprecated part of the API. Used only for smoother transition between scala and akka actors
   */  
  protected[actors] override def reply(msg: Any) = super.reply(msg)

  override def forward(msg: Any) = super.forward(msg)

  override def reactWithin(msec: Long)(handler: PartialFunction[Any, Unit]): Nothing =
    super.reactWithin(msec)(handler)
  
  override def act(): Unit = internalAct()    

  override def start(): StashingActor = {
    super.start()
    this
  }    

  override def receive[R](f: PartialFunction[Any, R]): R

  /*
   * Internal implementation.
   */

  private[actors] var behaviorStack = immutable.Stack[PartialFunction[Any, Unit]]()

  /*
   * Checks that StashingActor can be created only by MigrationSystem.actorOf method.
   */
  private[this] def creationCheck = {

    // creation check (see ActorRef)
    val context = MigrationSystem.contextStack.get
    if (context.isEmpty)
      throw new RuntimeException("In order to create StashingActor one must use actorOf.")
    else {
      if (!context.head)
        throw new RuntimeException("Only one actor can be created per actorOf call.")
      else
        MigrationSystem.contextStack.set(context.push(false))
    }

  }

  private[actors] override def preAct() {
    preStart()
  }

  /** Adds message to a stash, to be processed later. Stashed messages can be fed back into the $actor's
   *  mailbox using <code>unstashAll()</code>.
   *  
   *  Temporarily stashing away messages that the $actor does not (yet) handle simplifies implementing
   *  certain messaging protocols.
   */
  final override def stash(msg: Any): Unit = super.stash(msg)         

  /** Returns the whole stash back to the mailbox of an actor.
   * 
   */
  final override def unstashAll(): Unit = super.unstashAll()    
  
  /**
   * Wraps any partial function with Exit message handling.
   */
  private[actors] def wrapWithSystemMessageHandling(pf: PartialFunction[Any, Unit]): PartialFunction[Any, Unit] = {          
    
    def swapExitHandler(pf: PartialFunction[Any, Unit]) = new PartialFunction[Any, Unit] {
      def swapExit(v: Any) = v match {
          case Exit(from, reason) =>
            Terminated(new InternalActorRef(from.asInstanceOf[InternalActor]))
          case v => v
      }
      
      def isDefinedAt(v: Any) = pf.isDefinedAt(swapExit(v))
      
      def apply(v: Any) = pf(swapExit(v))
    }
    
     swapExitHandler(pf orElse {
      case m => unhandled(m)
    })
  }
  
  /*
   * Method that models the behavior of Akka actors.  
   */
  private[actors] def internalAct() {
    trapExit = true
    behaviorStack = behaviorStack.push(wrapWithSystemMessageHandling(receive))    
    loop {            
        if (receiveTimeout.isDefined)          
          reactWithin(receiveTimeout.get)(behaviorStack.top)
        else
          react(behaviorStack.top)
    }  
  }

  private[actors] override def internalPostStop() = postStop()

  lazy val ReceiveTimeout = TIMEOUT
  
  /**
   * Used to simulate Akka context behavior. Should be used only for migration purposes.
   */
  protected[actors] class ActorContext(val ref: InternalActorRef) {
    
    /**
    * Shuts down the actor its dispatcher and message queue.
    */
    def stop(subject: ActorRef): Nothing = if (subject != ref) 
      throw new RuntimeException("Only stoping of self is allowed during migration.") 
    else     
      ref.localActor.exit()
    
    /**
    * Registers this actor as a Monitor for the provided ActorRef.
    * @return the provided ActorRef
    */
    def watch(subject: ActorRef): ActorRef = {
      ref.localActor.link(subject)
      // TODO (VJ) change to unidirectional 
      //ref.localActor.watch(subject)
      subject
    }

     /**
     * Unregisters this actor as Monitor for the provided ActorRef.
     * @return the provided ActorRef
     */
     def unwatch(subject: ActorRef): ActorRef = {
       ref.localActor.unlink(subject)
       // TODO (VJ) change to unidirectional
       // ref.localActor unwatch subject
       subject
     }    
  }
}

/**
 * This message is thrown by default when an Actors behavior doesn't match a message
 */
case class UnhandledMessageException(msg: Any, ref: ActorRef = null) extends Exception {

  def this(msg: String) = this(msg, null)

  // constructor with 'null' ActorRef needed to work with client instantiation of remote exception
  override def getMessage =
    if (ref ne null) "Actor %s does not handle [%s]".format(ref, msg)
    else "Actor does not handle [%s]".format(msg)

  override def fillInStackTrace() = this //Don't waste cycles generating stack trace
}

case class Terminated(actor: ActorRef)
