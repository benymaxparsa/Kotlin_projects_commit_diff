/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okhttp3.internal.connection

import java.io.IOException
import okhttp3.Address
import okhttp3.HttpUrl

/**
 * Policy on choosing which connection to use for an exchange and any retries that follow. This uses
 * the following strategies:
 *
 *  1. If the current call already has a connection that can satisfy the request it is used. Using
 *     the same connection for an initial exchange and its follow-ups may improve locality.
 *
 *  2. If there is a connection in the pool that can satisfy the request it is used. Note that it is
 *     possible for shared exchanges to make requests to different host names! See
 *     [RealConnection.isEligible] for details.
 *
 *  3. If there's no existing connection, make a list of routes (which may require blocking DNS
 *     lookups) and attempt a new connection them. When failures occur, retries iterate the list of
 *     available routes.
 *
 * If the pool gains an eligible connection while DNS, TCP, or TLS work is in flight, this finder
 * will prefer pooled connections. Only pooled HTTP/2 connections are used for such de-duplication.
 *
 * It is possible to cancel the finding process by canceling its call.
 *
 * Implementations of this interface are not thread-safe. Each instance is thread-confined to the
 * thread executing the call.
 */
interface RoutePlanner {
  val address: Address

  fun isCanceled(): Boolean

  /** Returns a plan to attempt. */
  @Throws(IOException::class)
  fun plan(): Plan

  fun trackFailure(e: IOException)

  /** Returns true if this planner has received any failures. */
  fun hasFailure(): Boolean

  /** Returns true if this planner has more routes to try. */
  fun hasMoreRoutes(): Boolean

  /**
   * Returns true if the host and port are unchanged from when this was created. This is used to
   * detect if followups need to do a full connection-finding process including DNS resolution, and
   * certificate pin checks.
   */
  fun sameHostAndPort(url: HttpUrl): Boolean

  /**
   * A plan holds either an immediately-usable connection, or one that must be connected first.
   * These steps are split so callers can call [connect] on a background thread if attempting
   * multiple plans concurrently.
   */
  interface Plan {
    val isConnected: Boolean

    fun connect(): ConnectResult

    fun handleSuccess(): RealConnection

    fun cancel()
  }

  /**
   * What to do once a plan has executed.
   *
   * If [nextPlan] is not-null, another attempt should be made by following it. If [throwable] is
   * non-null, it should be reported to the user should all further attempts fail.
   *
   * The two values are independent: results can contain both (recoverable error), neither
   * (success), just an exception (permanent failure), or just a plan (non-exceptional retry).
   */
  data class ConnectResult(
    val plan: Plan,
    val nextPlan: Plan? = null,
    val throwable: Throwable? = null,
  )
}
