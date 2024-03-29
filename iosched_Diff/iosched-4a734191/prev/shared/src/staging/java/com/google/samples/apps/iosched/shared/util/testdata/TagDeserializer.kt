/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.apps.iosched.shared.util.testdata

import android.graphics.Color
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.samples.apps.iosched.shared.model.Tag
import com.google.samples.apps.iosched.shared.util.ColorUtils
import timber.log.Timber
import java.lang.reflect.Type

/**
 * Deserializer for [Tag]s.
 */
class TagDeserializer : JsonDeserializer<Tag> {

    override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
    ): Tag {
        val obj = json?.asJsonObject!!
        val colorString = obj.get("color")?.asString
        val color = if (colorString != null) {
            try {
                ColorUtils.parseHexColor(colorString)
            } catch (t: Throwable) {
                Timber.d(t, "Falied to parse tag color")
                Color.TRANSPARENT
            }
        } else {
            Color.TRANSPARENT
        }
        return Tag(
                id = obj.get("tag").asString,
                category = obj.get("category").asString,
                orderInCategory = obj.get("order_in_category")?.asInt ?: 999,
                color = color,
                name = obj.get("name").asString
        )
    }
}
