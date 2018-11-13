import com.couchbase.client.scala._

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Samples {
  def blockingApi(): Unit = {
    // Just for demoing, really this would come from cluster.openCollection("scope", "people") or similar
    val coll = new Collection()

    // Gets return Option[JsonDocument]
    val fetched1 = coll.get("id")
    if (fetched1.isDefined) {
      // do something with fetched1.get
    }

    // getOrError is a convenience method that either returns JsonDocument (no Option) or throws DocumentNotFoundException
    val fetched2 = coll.getOrError("id")

    // All parameters provided by named/default parameters rather than a GetOptions class.  Not wedded to
    // this, just feel it's more idiomatically Scala
    val fetched3 = coll.get("id", timeout = 1000.milliseconds)

    // getAndLock and getAndTouch work pretty much the same as get
    val fetched4 = coll.getAndLock("id", 5.seconds)

    // Basic insert
    val toInsert = JsonDocument.create("id", JsonObject.empty())
    val docPostInsert = coll.insert(toInsert)

    // Basic insert without creating a JsonDocument
    // One downside of named/default params is that I have to give this a different name than 'insert'
    coll.insertContent("id", JsonObject.empty())

    // Insert - showing all parameters
    coll.insert(toInsert,
      timeout = 1000.milliseconds,
      expiration = 10.days,
      replicateTo = ReplicateTo.ALL,
      persistTo = PersistTo.MAJORITY
    )

    // Insert, providing a subset of parameters
    coll.insert(toInsert, timeout = 1000.milliseconds, persistTo = PersistTo.MAJORITY)

    // Basic replaces.  JsonDocument is an immutable Scala case class and it's trivial to copy
    // it with different content:
    if (fetched1.isDefined) {
      val toReplace = fetched1.get.copy(content = JsonObject.empty())
      coll.replace(toReplace)

      // Replace, providing a subset of parameters
      coll.replace(toReplace, timeout = 1000.milliseconds, persistTo = PersistTo.MAJORITY)
    }
  }


  // There are two asynchronous APIs.  This one returns Scala Futures (similar to a Java CompletableFuture)
  def asyncApi(): Unit = {

    // When using Scala Futures you tell them how to run (thread-pools etc.) with an ExecutionContext (similar to a
    // Java executor), normally provided as an implicit argument (e.g. all Futures below will automatically use this
    // variable, as it's in-scope, marked implicit, and has the correct type).  This basic one is a simple thread-pool.
    implicit val ec = ExecutionContext.Implicits.global

    // Just for demoing, really this would come from cluster.openCollection("scope", "people") or similar
    val coll = new AsyncCollection()


    // Gets return Future[Option[JsonDocument]].  A basic way to handle a Future's result is this:
    coll.get("id", timeout = 1000.milliseconds) onComplete {
      case Success(doc) =>
        // doc is an Option[JsonDocument]
        if (doc.isDefined) println("Got a doc!")
        else println("No doc :(")

      case Failure(err) =>
        // err is a RuntimeException
        println("Error! " + err)
    }

    // Or block on it (discouraged)
    val getFuture = coll.get("id")
    val doc = Await.result(getFuture, atMost = 5.seconds)

    // Futures are powerful and support things like map and filter.  Many of the operations supported by Project Reactor
    // are possible with Futures (though they're missing backpressure and many of Reactor's more advanced operators)
    // Get-and-replace
    val replace = coll.getOrError("id", timeout = 1000.milliseconds)
      .map(doc => {
        val toReplace = doc.copy(content = JsonObject.empty())

        // Replace, providing a subset of parameters
        coll.replace(toReplace, timeout = 1000.milliseconds, persistTo = PersistTo.MAJORITY)
      })

    Await.result(replace, atMost = 5.seconds)

    // Another, maybe tidier way of writing that get-replace
    val replace2 = for {
      doc <- coll.getOrError("id", timeout = 1000.milliseconds)
      doc <- coll.replace(doc.copy(content = JsonObject.empty()))
    } yield doc

    Await.result(replace, atMost = 5.seconds)

    // Insert
    val toInsert = JsonDocument.create("id", JsonObject.empty())
    coll.insert(toInsert, timeout = 1000.milliseconds, persistTo = PersistTo.MAJORITY) onComplete {
      case Success(doc) =>
      case Failure(err) =>
    }
  }
}