/*
 * Copyright (C) 2016 Square, Inc.
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
package okhttp3.internal.http

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.checkDuration
import okhttp3.internal.connection.Exchange
import okhttp3.internal.connection.RealCall

/**
 * A concrete interceptor chain that carries the entire interceptor chain: all application
 * interceptors, the OkHttp core, all network interceptors, and finally the network caller.
 *
 * If the chain is for an application interceptor then [exchange] must be null. Otherwise it is for
 * a network interceptor and [exchange] must be non-null.
 */
class RealInterceptorChain(
  private val interceptors: List<Interceptor>,
  private val call: RealCall,
  private val exchange: Exchange?,
  private val index: Int,
  private val request: Request,
  private val connectTimeout: Int,
  private val readTimeout: Int,
  private val writeTimeout: Int
) : Interceptor.Chain {

  private var calls: Int = 0

  override fun connection(): Connection? = exchange?.connection()

  override fun connectTimeoutMillis(): Int = connectTimeout

  override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain {
    val millis = checkDuration("timeout", timeout.toLong(), unit)
    return RealInterceptorChain(interceptors, call, exchange, index, request, millis,
        readTimeout, writeTimeout)
  }

  override fun readTimeoutMillis(): Int = readTimeout

  override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain {
    val millis = checkDuration("timeout", timeout.toLong(), unit)
    return RealInterceptorChain(interceptors, call, exchange, index, request, connectTimeout,
        millis, writeTimeout)
  }

  override fun writeTimeoutMillis(): Int = writeTimeout

  override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain {
    val millis = checkDuration("timeout", timeout.toLong(), unit)
    return RealInterceptorChain(interceptors, call, exchange, index, request, connectTimeout,
        readTimeout, millis)
  }

  fun exchange(): Exchange = exchange!!

  override fun call(): RealCall = call

  override fun request(): Request = request

  override fun proceed(request: Request): Response {
    return proceed(request, exchange)
  }

  @Throws(IOException::class)
  fun proceed(request: Request, exchange: Exchange?): Response {
    if (index >= interceptors.size) throw AssertionError()

    calls++

    // If we already have an exchange, confirm that the incoming request will use it.
    check(this.exchange == null || this.exchange.connection()!!.supportsUrl(request.url)) {
      "network interceptor ${interceptors[index - 1]} must retain the same host and port"
    }

    // If we already have an exchange, confirm that this is the only call to chain.proceed().
    check(this.exchange == null || calls <= 1) {
      "network interceptor ${interceptors[index - 1]} must call proceed() exactly once"
    }

    // Call the next interceptor in the chain.
    val next = RealInterceptorChain(interceptors, call, exchange,
        index + 1, request, connectTimeout, readTimeout, writeTimeout)
    val interceptor = interceptors[index]

    @Suppress("USELESS_ELVIS")
    val response = interceptor.intercept(next) ?: throw NullPointerException(
        "interceptor $interceptor returned null")

    // Confirm that the next interceptor made its required call to chain.proceed().
    check(exchange == null || index + 1 >= interceptors.size || next.calls == 1) {
      "network interceptor $interceptor must call proceed() exactly once"
    }

    check(response.body != null) { "interceptor $interceptor returned a response with no body" }

    return response
  }
}