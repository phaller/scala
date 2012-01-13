package scala.concurrent
package default



import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import scala.concurrent.forkjoin.{ ForkJoinPool, RecursiveAction, ForkJoinWorkerThread }
import scala.util.{ Timeout, Duration }
import scala.annotation.tailrec



private[concurrent] trait Completable[T] {
  self: Future[T] =>
  
  val executionContext: ExecutionContextImpl

  type Callback = Either[Throwable, T] => Any

  def getState: State[T]

  def casState(oldv: State[T], newv: State[T]): Boolean

  protected def dispatch[U](r: Runnable) = executionContext execute r
  
  protected def processCallbacks(cbs: List[Callback], r: Either[Throwable, T]) =
    for (cb <- cbs) dispatch(new Runnable {
      override def run() = cb(r)
    })

  def future: Future[T] = self
  
  def onComplete[U](callback: Either[Throwable, T] => U): this.type = {
    @tailrec def tryAddCallback(): Either[Throwable, T] = {
      getState match {
        case p @ Pending(lst) =>
          val pt = p.asInstanceOf[Pending[T]]
          if (casState(pt, Pending(callback :: pt.callbacks))) null
          else tryAddCallback()
        case Success(res) => Right(res)
        case Failure(t) => Left(t)
      }
    }
    
    val res = tryAddCallback()
    if (res != null) dispatch(new Runnable {
      override def run() =
        try callback(res)
        catch handledFutureException andThen {
          t => Console.err.println(t)
        }
    })
    
    this
  }
  
  def isTimedout: Boolean = getState match {
    case Failure(ft: FutureTimeoutException) => true
    case _ => false
  }
  
}

private[concurrent] class PromiseImpl[T](context: ExecutionContextImpl)
  extends Promise[T] with Future[T] with Completable[T] {
 
  val executionContext: scala.concurrent.default.ExecutionContextImpl = context

  @volatile private var state: State[T] = _

  val updater = AtomicReferenceFieldUpdater.newUpdater(classOf[PromiseImpl[T]], classOf[State[T]], "state")

  updater.set(this, Pending(List()))
  
  def casState(oldv: State[T], newv: State[T]): Boolean = {
    updater.compareAndSet(this, oldv, newv)
  }

  def getState: State[T] = {
    updater.get(this)
  }

  @tailrec private def tryCompleteState(completed: State[T]): List[Callback] = (getState: @unchecked) match {
    case p @ Pending(cbs) => if (!casState(p, completed)) tryCompleteState(completed) else cbs
    case _ => null
  }
  
  /** Completes the promise with a value.
   *  
   *  @param value    The value to complete the promise with.
   *  
   *  $promiseCompletion
   */
  def success(value: T): Unit = {
    val cbs = tryCompleteState(Success(value))
    if (cbs == null)
      throw new IllegalStateException
    else {
      processCallbacks(cbs, Right(value))
      this.synchronized {
        this.notifyAll()
      }
    }
  }

  /** Completes the promise with an exception.
   *  
   *  @param t        The throwable to complete the promise with.
   *  
   *  $promiseCompletion
   */
  def failure(t: Throwable): Unit = {
    val wrapped = wrap(t)
    val cbs = tryCompleteState(Failure(wrapped))
    if (cbs == null)
      throw new IllegalStateException
    else {
      processCallbacks(cbs, Left(wrapped))
      this.synchronized {
        this.notifyAll()
      }
    }
  }
  
  def await(timeout: Timeout)(implicit canblock: scala.concurrent.CanBlock): T = getState match {
    case Success(res) => res
    case Failure(t)   => throw t
    case _ =>
      this.synchronized {
        while (true)
          getState match {
            case Pending(_)   => this.wait()
            case Success(res) => return res
            case Failure(t)   => throw t
          }
      }
      sys.error("unreachable")
  }
  
}

private[concurrent] class TaskImpl[T](context: ExecutionContextImpl, body: => T)
  extends RecursiveAction with Task[T] with Future[T] with Completable[T] {

  val executionContext: ExecutionContextImpl = context

  @volatile private var state: State[T] = _

  val updater = AtomicReferenceFieldUpdater.newUpdater(classOf[TaskImpl[T]], classOf[State[T]], "state")

  updater.set(this, Pending(List()))
  
  def casState(oldv: State[T], newv: State[T]): Boolean = {
    updater.compareAndSet(this, oldv, newv)
  }

  def getState: State[T] = {
    updater.get(this)
  }

  @tailrec private def tryCompleteState(completed: State[T]): List[Callback] = (getState: @unchecked) match {
    case p @ Pending(cbs) => if (!casState(p, completed)) tryCompleteState(completed) else cbs
  }
  
  def compute(): Unit = {
    var cbs: List[Callback] = null
    try {
      val res = body
      processCallbacks(tryCompleteState(Success(res)), Right(res))
    } catch {
      case t if isFutureThrowable(t) =>
        processCallbacks(tryCompleteState(Failure(t)), Left(t))
      case t =>
        val ee = new ExecutionException(t)
        processCallbacks(tryCompleteState(Failure(ee)), Left(ee))
        throw t
    }
  }
  
  def start(): Unit = {
    Thread.currentThread match {
      case fj: ForkJoinWorkerThread if fj.getPool eq executionContext.pool => fork()
      case _ => executionContext.pool.execute(this)
    }
  }
  
  // TODO FIXME: handle timeouts
  def await(atMost: Duration): this.type =
    await
  
  def await: this.type = {
    this.join()
    this
  }
  
  def tryCancel(): Unit =
    tryUnfork()
  
  def await(timeout: Timeout)(implicit canblock: CanBlock): T = {
    join() // TODO handle timeout also
    (updater.get(this): @unchecked) match {
      case Success(r) => r
      case Failure(t) => throw t
    }
  }
  
}


private[concurrent] sealed abstract class State[T]


case class Pending[T](callbacks: List[Either[Throwable, T] => Any]) extends State[T]


case class Success[T](result: T) extends State[T]


case class Failure[T](throwable: Throwable) extends State[T]


private[concurrent] final class ExecutionContextImpl extends ExecutionContext {
  val pool = {
    val p = new ForkJoinPool
    p.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler {
      def uncaughtException(t: Thread, throwable: Throwable) {
        Console.err.println(throwable.getMessage)
        throwable.printStackTrace(Console.err)
      }
    })
    p
  }

  @inline
  private def executeTask(task: RecursiveAction) {
    if (Thread.currentThread.isInstanceOf[ForkJoinWorkerThread])
      task.fork()
    else
      pool execute task
  }

  def execute(task: Runnable) {
    val action = new RecursiveAction { def compute() { task.run() } }
    executeTask(action)
  }
  
  def execute[U](body: () => U) {
    val action = new RecursiveAction { def compute() { body() } }
    executeTask(action)
  }
  
  def task[T](body: => T): Task[T] = {
    new TaskImpl(this, body)
  }
  
  def future[T](body: => T): Future[T] = {
    val t = task(body)
    t.start()
    t.future
  }
  
  def promise[T]: Promise[T] =
    new PromiseImpl[T](this)
  
  // TODO fix the timeout
  def blockingCall[T](timeout: Timeout, b: Awaitable[T]): T = b match {
    case fj: TaskImpl[_] if fj.executionContext.pool eq pool =>
      fj.await(timeout)
    case _ =>
      var res: T = null.asInstanceOf[T]
      @volatile var blockingDone = false
      // TODO add exception handling here!
      val mb = new ForkJoinPool.ManagedBlocker {
        def block() = {
          res = b.await(timeout)(CanBlockEvidence)
          blockingDone = true
          true
        }
        def isReleasable = blockingDone
      }
      ForkJoinPool.managedBlock(mb, true)
      res
  }

}