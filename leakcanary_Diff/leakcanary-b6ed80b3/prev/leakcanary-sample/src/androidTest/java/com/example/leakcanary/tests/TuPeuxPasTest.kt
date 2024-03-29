package com.example.leakcanary.tests

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.rule.ActivityTestRule
import com.example.leakcanary.MainActivity
import com.example.leakcanary.R
import org.junit.Rule
import org.junit.Test

/**
 * This UI test looks like it should succeed, but it will actually fail because
 * it triggers a leak.
 *
 * Run this test with:
 *
 * ./gradlew leakcanary-sample:connectedCheck
 *
 * To set this up, we installed a special RefWatcher dedicated to detecting leaks in
 * instrumentation tests in [InstrumentationExampleApplication], and then added the FailTestOnLeakRunListener
 * to the config of our build.gradle:
 *
 * testInstrumentationRunnerArgument "listener", "com.squareup.leakcanary.FailTestOnLeakRunListener"
 *
 *
 * Why is this class named "TuPeuxPasTest"?
 *
 * This test fails, intentionally. In French, "Tu peux pas test" could mean "you cannot test"
 * written with poor grammar. Except, that's not what it means.
 * If you're curious, interested in French and have time to waste:
 * https://www.youtube.com/watch?v=DZZpbmAc-0A
 * https://www.youtube.com/watch?v=nHeAA6X-XUQ
 */
class TuPeuxPasTest {

  @get:Rule
  var activityRule = ActivityTestRule(MainActivity::class.java)

  @Test
  fun clickAsyncWork() {
    onView(withId(R.id.async_work)).perform(click())
  }

  @Test
  fun asyncButtonHasStartText() {
    onView(withId(R.id.async_work)).check(matches(withText(R.string.start_async_work)))
  }
}
