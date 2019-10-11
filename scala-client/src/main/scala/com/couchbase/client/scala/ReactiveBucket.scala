/*
 * Copyright (c) 2019 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.couchbase.client.scala

import com.couchbase.client.core.annotation.Stability
import com.couchbase.client.core.error.ViewServiceException
import com.couchbase.client.core.msg.view.ViewRequest
import com.couchbase.client.scala.query.handlers.ViewHandler
import com.couchbase.client.scala.util.FutureConversions
import com.couchbase.client.scala.view._
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.compat.java8.OptionConverters._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/** Represents a Couchbase bucket resource.
  *
  * This is the reactive version of the [[Bucket]] API.
  *
  * Applications should not create these manually, but instead use the functions in [[Cluster]].
  *
  * @param async the async bucket reference
  * @param ec   an ExecutionContext to use for any Future.  Will be supplied automatically as long as resources are
  *             opened in the normal way, starting from functions in [[Cluster]]
  *
  * @author Graham Pople
  * @since 1.0.0
  */
class ReactiveBucket private[scala](val async: AsyncBucket) {
  private[scala] implicit val ec: ExecutionContext = async.ec
  private[scala] val viewHandler = new ViewHandler

  /** Opens and returns a Couchbase scope resource.
    *
    * @param scopeName the name of the scope
    */
  @Stability.Volatile
  def scope(scopeName: String): SMono[ReactiveScope] = {
    SMono.fromFuture(async.scope(scopeName)).map(v => new ReactiveScope(v, async.name))
  }

  /** Opens and returns the default Couchbase scope. */
  @Stability.Volatile
  def defaultScope: SMono[ReactiveScope] = {
    scope(DefaultResources.DefaultScope)
  }

  /** Returns the Couchbase default collection resource. */
  def defaultCollection: SMono[ReactiveCollection] = {
    scope(DefaultResources.DefaultScope).flatMap(v => v.defaultCollection)
  }

  /** Opens a Couchbase collection resource on the default scope.
    *
    * @param collectionName the name of the collection
    *
    * @return a created collection resource
    */
  @Stability.Volatile
  def collection(collectionName: String): SMono[ReactiveCollection] = {
    scope(DefaultResources.DefaultScope).flatMap(v => v.collection(collectionName))
  }

  /** Performs a view query against the cluster.
    *
    * This is a reactive streaming version of this API.  See [[Bucket]] for a synchronous blocking version, and
    * [[Bucket.async]] for an async version.
    *
    * @param designDoc the view design document to use
    * @param viewName  the view to use
    * @param options   any view query options - see [[view.ViewOptions]] for documentation
    *
    * @return a `Mono` containing a [[view.ViewResult]] (which includes any returned rows)
    */
  def viewQuery(designDoc: String,
                viewName: String,
                options: ViewOptions = ViewOptions()): SMono[ReactiveViewResult] = {
    val req = viewHandler.request(designDoc, viewName, options, async.core, async.environment, async.name)
    viewQuery(req)
  }

  private def viewQuery(req: Try[ViewRequest]): SMono[ReactiveViewResult] = {
    req match {
      case Failure(err) =>
        SMono.raiseError(err)

      case Success(request) =>

        FutureConversions.javaCFToScalaMono(request, request.response(), false)
          .map(response => {

            val rows: SFlux[ViewRow] = FutureConversions.javaFluxToScalaFlux(response.rows())

              .map[ViewRow](bytes => ViewRow(bytes.data()))

              .flatMap(_ => FutureConversions.javaMonoToScalaMono(response.trailer()))

              // Check for errors
              .flatMap(trailer => {
              trailer.error().asScala match {
                case Some(err) =>
                  val msg = "Encountered view error '" + err.error() + "' with reason '" + err.reason() + "'"
                  val error = new ViewServiceException(msg)
                  SMono.raiseError(error)
                case _ => SMono.empty
              }
            })

            val meta = ViewMetaData(
              response.header().debug().asScala.map(v => ViewDebug(v)),
              response.header().totalRows())

            ReactiveViewResult(SMono.just(meta), rows)
          })
    }
  }
}
