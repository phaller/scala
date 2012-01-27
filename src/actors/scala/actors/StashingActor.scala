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

  def self: ActorRef = new InternalActorRef(this)

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
  def handle: Receive

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
    behaviorStack.push()
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

  protected[actors] override def exceptionHandler: PartialFunction[Exception, Unit] = {
    case e =>
    // does not restart the method
    //   restart
    //   postRestart()      
    // else 
    //   ????       
    // TODO (VJ) how to this??? 
  }

  protected[actors] override def scheduler: IScheduler = super.scheduler

  protected[actors] override def mailboxSize: Int = super.mailboxSize

  override def getState: Actor.State.Value = super.getState

  protected[actors] override def exit(reason: AnyRef): Nothing = {
    super.exit(reason)
  }

  protected[actors] override def exit(): Nothing = {
    super.exit()
  }

  override def start(): StashingActor = synchronized {
    super.start()
    this
  }

  override def link(to: AbstractActor): AbstractActor = super.link(to)

  override def link(body: => Unit): Actor = super.link(body)

  override def unlink(from: AbstractActor) = super.unlink(from)

  override def ? : Any = super.?

  override def send(msg: Any, replyTo: OutputChannel[Any]) = super.send(msg, replyTo) 
  
  override def receiver: Actor = super.receiver

  override def restart: Unit

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

  /*
   * Method that models the behavior of Akka actors.  
   */
  private[actors] def internalAct() {

    behaviorStack = behaviorStack.push(new PartialFunction[Any, Unit] {
      def isDefinedAt(x: Any) =
        handle.isDefinedAt(x)
      def apply(x: Any) = handle(x)
    } orElse {
      case m => unhandled(m)
    })

    
    loop {
      if (receiveTimeout.isDefined)
        reactWithin(receiveTimeout.get)(behaviorStack.top)
      else
        react(behaviorStack.top)
    }    
  }

  private[actors] override def internalPostStop() = postStop()

  lazy val ReceiveTimeout = TIMEOUT
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
