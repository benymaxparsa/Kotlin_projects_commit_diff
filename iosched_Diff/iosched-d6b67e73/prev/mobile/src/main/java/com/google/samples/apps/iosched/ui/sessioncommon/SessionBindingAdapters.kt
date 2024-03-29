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

package com.google.samples.apps.iosched.ui.sessioncommon

import android.content.Context
import android.databinding.BindingAdapter
import android.graphics.Color.TRANSPARENT
import android.graphics.drawable.GradientDrawable
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.widget.TextView
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.shared.model.Tag

@BindingAdapter("sessionTags")
fun sessionTags(recyclerView: RecyclerView, sessionTags: List<Tag>?) {
    recyclerView.adapter = (recyclerView.adapter as? TagAdapter ?: TagAdapter())
        .apply {
            tags = sessionTags ?: emptyList()
        }
}

@BindingAdapter("tagTint")
fun tagTint(textView: TextView, color: Int) {
    // Tint the colored dot
    (textView.compoundDrawablesRelative[0] as? GradientDrawable)?.setColor(
        tagTintOrDefault(
            color,
            textView.context
        )
    )
}

/**
 * Creates a tag background using the tag's color and sets text color according to the background.
 */
@BindingAdapter("tagChip")
fun tagChip(textView: TextView, tag: Tag) {
    textView.background = (textView.resources.getDrawable(
        R.drawable.tag_filled, textView.context.theme
    ) as GradientDrawable).apply {
        setColor(tagTintOrDefault(tag.color, textView.context))
    }

    val textColor = if (tag.isLightFontColor()) {
        // tag has a relatively dark background
        R.color.tag_filter_text_dark
    } else {
        R.color.tag_filter_text_light
    }
    textView.setTextColor(ContextCompat.getColor(textView.context, textColor))
}

fun tagTintOrDefault(color: Int, context: Context): Int {
    return if (color != TRANSPARENT) {
        color
    } else {
        ContextCompat.getColor(context, R.color.default_tag_color)
    }
}
