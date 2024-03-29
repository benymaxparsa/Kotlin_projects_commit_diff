/*
 * Copyright (C) 2018 Square, Inc.
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
package okhttp3.internal.duplex

import mockwebserver3.MockResponse
import mockwebserver3.internal.duplex.DuplexResponseBody

/**
 * Internal access to MockWebServer APIs. Don't use this, don't use internal, these APIs are not
 * stable.
 */
abstract class MwsDuplexAccess {

  abstract fun setBody(
    mockResponseBuilder: MockResponse.Builder,
    duplexResponseBody: DuplexResponseBody,
  )

  companion object {
    @JvmField var instance: MwsDuplexAccess? = null
  }
}
