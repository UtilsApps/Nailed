package it.utils.nailed;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.espresso.accessibility.AccessibilityChecks;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static org.hamcrest.Matchers.anything;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static org.hamcrest.Matchers.anything;

@RunWith(AndroidJUnit4.class)
public class BurstTest {

    @Rule
    public ActivityScenarioRule<MainActivity> creditsActivityScenarioRule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Before
    public void setUp() {
        AccessibilityChecks.enable();
    }

    @Test
    @LargeTest
    public void accessibilityChecks() {
        //Todo long test (5s), try to make it faster

        //TODO
        //launch main activity, check that no timer/service is running
        //click start burst, wait, check that timer/service is running
        //chack that pictures are being saved
        //stop burst, check that no timer/service is running

        //check that service keeps going on even after changin to other app without quitting
        //(it must be probably a foreground service not to be closed automatically by the OS)

        //TODO find a way to check all the items in the list view,
        // also in a more efficient way (like not doing anything on it)
        // than performing a click
        /*onData(anything())
                .inAdapterView(withContentDescription("Credits list"))
                .atPosition(3)
                .perform(click());*/

        //TODO test for error (happens in android 11 when denied access to camera):
        // Foreground service started from background can not have \
        // location/camera/microphone access: service SERVICE_NAME
    }
}