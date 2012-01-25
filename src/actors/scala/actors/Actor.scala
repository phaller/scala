/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2005-2011, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */


package scala.actors

import scala.util.control.ControlThrowable
import java.util.{TimerTask}

/**
 * Provides functions for the definition of
 * actors, as well as actor operations, such as
 * <code>receive</code>, <code>react</code>, <code>reply</code>,
 * etc.
 *
 * @author Philipp Haller
 */
object Actor extends Combinators {

  /** State of an actor.
   *  <ul>
   *    <li><b>New</b> - 
   *      Not yet started</li>
   *    <li><b>Runnable</b> - 
   *      Executing</li>
   *    <li><b>Suspended</b> - 
   *      Suspended, waiting in a `react`</li>
   *    <li><b>TimedSuspended</b> - 
   *      Suspended, waiting in a `reactWithin` </li>
   *    <li><b>Blocked</b> - 
   *      Blocked waiting in a `receive` </li>
   *    <li><b>TimedBlocked</b> - 
   *      Blocked waiting in a `receiveWithin` </li>
   *    <li><b>Terminated</b> - 
   *      Actor has terminated </li>
   *  </ul>
   */
  object State extends Enumeration {
    val New,
        Runnable,
        Suspended,
        TimedSuspended,
        Blocked,
        TimedBlocked,
        Terminated = Value
  }

  private[actors] val tl = new ThreadLocal[InternalReplyReactor]

  // timer thread runs as daemon
  private[actors] val timer = new java.util.Timer(true)

  private[actors] val suspendException = new SuspendActorControl

  /**
   * Returns the currently executing actor. Should be used instead
   * of <code>this</code> in all blocks of code executed by
   * actors.
   *
   * @return returns the currently executing actor.
   */
  def self: Actor = self(Scheduler)

  private[actors] def self(sched: IScheduler): Actor =
    rawSelf(sched).asInstanceOf[Actor]

  private[actors] def rawSelf: InternalReplyReactor =
    rawSelf(Scheduler)

  private[actors] def rawSelf(sched: IScheduler): InternalReplyReactor = {
    val s = tl.get
    if (s eq null) {
      val r = new ActorProxy(Thread.currentThread, sched)
      tl.set(r)
      r
    } else
      s
  }

  private def parentScheduler: IScheduler = {
    val s = tl.get
    if (s eq null) Scheduler else s.scheduler
  }

  /**
   * Resets an actor proxy associated with the current thread.
   * It replaces the implicit <code>ActorProxy</code> instance
   * of the current thread (if any) with a new instance.
   *
   * This permits to re-use the current thread as an actor
   * even if its <code>ActorProxy</code> has died for some reason.
   */
  def resetProxy() {
    val a = tl.get
    if ((null ne a) && a.isInstanceOf[ActorProxy])
      tl.set(new ActorProxy(Thread.currentThread, parentScheduler))
  }

  /**
   * Removes any reference to an <code>Actor</code> instance
   * currently stored in thread-local storage.
   *
   * This allows to release references from threads that are
   * potentially long-running or being re-used (e.g. inside
   * a thread pool). Permanent references in thread-local storage
   * are a potential memory leak.
   */
  def clearSelf() {
    tl.set(null)
  }

  /**
   * Factory method for creating and starting an actor.
   *
   * @example {{{
   * import scala.actors.Actor._
   * ...
   * val a = actor {
   *   ...
   * }
   * }}}
   *
   * @param  body  the code block to be executed by the newly created actor
   * @return       the newly created actor. Note that it is automatically started.
   */
  def actor(body: => Unit): Actor = {
    val a = new Actor {
      def act() = body
      override final val scheduler: IScheduler = parentScheduler
    }
    a.start()
    a
  }

  /**
   * Factory method for creating actors whose
   * body is defined using a `Responder`.
   *
   * @example {{{
   * import scala.actors.Actor._
   * import Responder.exec
   * ...
   * val a = reactor {
   *   for {
   *     res <- b !! MyRequest;
   *     if exec(println("result: "+res))
   *   } yield {}
   * }
   * }}}
   *
   * @param  body  the `Responder` to be executed by the newly created actor
   * @return       the newly created actor. Note that it is automatically started.
   */
  def reactor(body: => Responder[Unit]): Actor = {
    val a = new Actor {
      def act() {
        Responder.run(body)
      }
      override final val scheduler: IScheduler = parentScheduler
    }
    a.start()
    a
  }

  /**
   * Receives the next message from the mailbox of the current actor
   * <code>self</code>.
   */
  def ? : Any = self.?

  /**
   * Receives a message from the mailbox of
   * <code>self</code>. Blocks if no message matching any of the
   * cases of <code>f</code> can be received.
   *
   * @example {{{
   * receive {
   *   case "exit" => println("exiting")
   *   case 42 => println("got the answer")
   *   case x:Int => println("got an answer")
   * }
   * }}}
   *
   * @param  f a partial function specifying patterns and actions
   * @return   the result of processing the received message
   */
  def receive[A](f: PartialFunction[Any, A]): A =
    self.receive(f)

  /**
   * Receives a message from the mailbox of
   * <code>self</code>. Blocks at most <code>msec</code>
   * milliseconds if no message matching any of the cases of
   * <code>f</code> can be received. If no message could be
   * received the <code>TIMEOUT</code> action is executed if
   * specified.
   *
   * @param  msec the time span before timeout
   * @param  f    a partial function specifying patterns and actions
   * @return      the result of processing the received message
   */
  def receiveWithin[R](msec: Long)(f: PartialFunction[Any, R]): R =
    self.receiveWithin(msec)(f)

  /**
   * Lightweight variant of <code>receive</code>.
   *
   * Actions in <code>f</code> have to contain the rest of the
   * computation of <code>self</code>, as this method will never
   * return.
   *
   * A common method of continuting the computation is to send a message
   * to another actor:
   * {{{
   * react {
   *   case Get(from) =>
   *     react {
   *       case Put(x) => from ! x
   *     }
   * }
   * }}}
   *
   * Another common method is to use `loop` to continuously `react` to messages:
   * {{{
   * loop {
   *   react {
   *     case Msg(data) => // process data
   *   }
   * }
   * }}}
   *
   * @param  f a partial function specifying patterns and actions
   * @return   this function never returns
   */
  def react(f: PartialFunction[Any, Unit]): Nothing =
    rawSelf.react(f)

  /**
   * Lightweight variant of <code>receiveWithin</code>.
   *
   * Actions in <code>f</code> have to contain the rest of the
   * computation of <code>self</code>, as this method will never
   * return.
   *
   * @param  msec the time span before timeout
   * @param  f    a partial function specifying patterns and actions
   * @return      this function never returns
   */
  def reactWithin(msec: Long)(f: PartialFunction[Any, Unit]): Nothing =
    self.reactWithin(msec)(f)

  def eventloop(f: PartialFunction[Any, Unit]): Nothing =
    rawSelf.react(new RecursiveProxyHandler(rawSelf, f))

  private class RecursiveProxyHandler(a: InternalReplyReactor, f: PartialFunction[Any, Unit])
          extends PartialFunction[Any, Unit] {
    def isDefinedAt(m: Any): Boolean =
      true // events are immediately removed from the mailbox
    def apply(m: Any) {
      if (f.isDefinedAt(m)) f(m)
      a.react(this)
    }
  }

  /**
   * Returns the actor which sent the last received message.
   */
  def sender: OutputChannel[Any] =
    rawSelf.internalSender

  /**
   * Sends <code>msg</code> to the actor waiting in a call to
   * <code>!?</code>.
   */
  def reply(msg: Any): Unit =
    rawSelf.reply(msg)

  /**
   * Sends <code>()</code> to the actor waiting in a call to
   * <code>!?</code>.
   */
  def reply(): Unit =
    rawSelf.reply(())

  /**
   * Returns the number of messages in <code>self</code>'s mailbox
   *
   * @return the number of messages in <code>self</code>'s mailbox
   */
  def mailboxSize: Int = rawSelf.mailboxSize

  /**
   * Converts a synchronous event-based operation into
   * an asynchronous `Responder`.
   *
   * @example {{{
   * val adder = reactor {
   *   for {
   *     _ <- respondOn(react) { case Add(a, b) => reply(a+b) }
   *   } yield {}
   * }
   * }}}
   */
  def respondOn[A, B](fun: PartialFunction[A, Unit] => Nothing):
    PartialFunction[A, B] => Responder[B] =
      (caseBlock: PartialFunction[A, B]) => new Responder[B] {
        def respond(k: B => Unit) = fun(caseBlock andThen k)
      }

  implicit def mkBody[a](body: => a) = new InternalActor.Body[a] {
    def andThen[b](other: => b): Unit = rawSelf.seq(body, other)
  }    

  /**
   * Links <code>self</code> to actor <code>to</code>.
   *
   * @param  to the actor to link to
   * @return    the parameter actor
   */
  def link(to: AbstractActor): AbstractActor = self.link(to)

  /**
   * Links <code>self</code> to the actor defined by <code>body</code>.
   *
   * @param body the body of the actor to link to
   * @return     the parameter actor
   */
  def link(body: => Unit): Actor = self.link(body)

  /**
   * Unlinks <code>self</code> from actor <code>from</code>.
   *
   * @param from the actor to unlink from
   */
  def unlink(from: AbstractActor): Unit = self.unlink(from)

  /**
   * <p>
   *   Terminates execution of <code>self</code> with the following
   *   effect on linked actors:
   * </p>
   * <p>
   *   For each linked actor <code>a</code> with
   *   <code>trapExit</code> set to <code>true</code>, send message
   *   <code>Exit(self, reason)</code> to <code>a</code>.
   * </p>
   * <p>
   *   For each linked actor <code>a</code> with
   *   <code>trapExit</code> set to <code>false</code> (default),
   *   call <code>a.exit(reason)</code> if
   *   <code>reason != 'normal</code>.
   * </p>
   */
  def exit(reason: AnyRef): Nothing = self.exit(reason)

  /**
   * <p>
   *   Terminates execution of <code>self</code> with the following
   *   effect on linked actors:
   * </p>
   * <p>
   *   For each linked actor <code>a</code> with
   *   <code>trapExit</code> set to <code>true</code>, send message
   *   <code>Exit(self, 'normal)</code> to <code>a</code>.
   * </p>
   */
  def exit(): Nothing = rawSelf.exit()

}

/**
 * <p>
 *   Provides lightweight, concurrent actors. Actors are
 *   created by extending the `Actor` trait (alternatively, one of the
 *   factory methods in its companion object can be used).  The
 *   behavior of an `Actor` subclass is defined by implementing its
 *   `act` method:
 *   
 *   {{{
 *   class MyActor extends Actor {
 *     def act() {
 *       // actor behavior goes here
 *     }
 *   }
 *   }}}
 *   
 *   A new `Actor` instance is started by invoking its `start` method.
 *   
 *   '''Note:''' care must be taken when invoking thread-blocking methods
 *   other than those provided by the `Actor` trait or its companion
 *   object (such as `receive`). Blocking the underlying thread inside
 *   an actor may lead to starvation of other actors. This also
 *   applies to actors hogging their thread for a long time between
 *   invoking `receive`/`react`.
 *   
 *   If actors use blocking operations (for example, methods for
 *   blocking I/O), there are several options:
 *   <ul>
 *     <li>The run-time system can be configured to use a larger thread pool size
 *     (for example, by setting the `actors.corePoolSize` JVM property).</li>
 *     
 *     <li>The `scheduler` method of the `Actor` trait can be overridden to return a 
 *     `ResizableThreadPoolScheduler`, which resizes its thread pool to
 *     avoid starvation caused by actors that invoke arbitrary blocking methods.</li>
 *     
 *     <li>The `actors.enableForkJoin` JVM property can be set to `false`, in which 
 *     case a `ResizableThreadPoolScheduler` is used by default to execute actors.</li>
 *   </ul>
 * </p>
 * <p>
 * The main ideas of the implementation are explained in the two papers
 * <ul>
 *   <li>
 *     <a href="http://lampwww.epfl.ch/~odersky/papers/jmlc06.pdf">
 *     <span style="font-weight:bold; white-space:nowrap;">Event-Based
 *     Programming without Inversion of Control</span></a>,
 *     Philipp Haller and Martin Odersky, <i>Proc. JMLC 2006</i>, and
 *   </li>
 *   <li>
 *     <a href="http://lamp.epfl.ch/~phaller/doc/haller07coord.pdf">
 *     <span style="font-weight:bold; white-space:nowrap;">Actors that
 *     Unify Threads and Events</span></a>,
 *     Philipp Haller and Martin Odersky, <i>Proc. COORDINATION 2007</i>.
 *   </li>
 * </ul>
 * </p>
 * 
 * @author Philipp Haller
 *
 * @define actor actor
 * @define channel actor's mailbox
 */
@SerialVersionUID(-781154067877019505L)
trait Actor extends InternalActor with ReplyReactor {
       
  override def start(): Actor = synchronized {
    super.start()
    this
  }  

}


