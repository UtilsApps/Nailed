package it.utils.nailed;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
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
import androidx.test.rule.ServiceTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.sql.Time;
import java.util.concurrent.TimeoutException;

import static androidx.core.content.ContextCompat.getSystemService;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static org.hamcrest.Matchers.anything;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static org.hamcrest.Matchers.anything;
import static org.junit.Assert.assertEquals;

//import static org.junit.Assert.assertThat;
//import static androidx.test.espresso.matcher.ViewMatchers.assertThat;

@RunWith(AndroidJUnit4.class)
public class BurstTest {

    /*
     * TODO
     * add test checking for active service/threads/timers
     * check there is online one service/timer at any given time
     * even if start burst clicked repeatedly
     * even if app quits and is started again, or started multiple times without closing it
     * */

    static String TAG = "BurstTest";

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    @Rule
    public ActivityScenarioRule<MainActivity> mainActivityScenarioRule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Before
    public void setUp() {
        AccessibilityChecks.enable();
    }

    @Test
    public void testWithStartedService() throws TimeoutException {

        assertEquals(false, isMyServiceRunning(PhotoBurstService.class));

        mServiceRule.startService(
                new Intent(InstrumentationRegistry.getInstrumentation().getContext(),
                        PhotoBurstService.class));//InstrumentationRegistry.getTargetContext()
        //do something
        assertEquals(true, isMyServiceRunning(PhotoBurstService.class));
        //mServiceRule.

        //ActivityScenario scenario = mainActivityScenarioRule.getScenario();
        //scenario.
    }

    public boolean isMyServiceRunning(@NonNull Class serviceClass) {//@NonNull Class<T> serviceClass
        /*ActivityManager manager
                = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE, serviceClass);*/
        ActivityManager manager = (ActivityManager)
                InstrumentationRegistry.getInstrumentation().getContext().getSystemService(
                        Context.ACTIVITY_SERVICE);

        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            Log.d(TAG, service.toString());
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testWithBoundService2() throws TimeoutException {
        IBinder binder = mServiceRule.bindService(
                //InstrumentationRegistry.getTargetContext()
                new Intent(InstrumentationRegistry.getInstrumentation().getContext(), PhotoBurstService.class));
        PhotoBurstService service = ((PhotoBurstService.LocalBinder) binder).getService();
        //assertTrue("True wasn't returned", service.doSomethingToReturnTrue());
    }

    @Test
    public void testWithBoundService() throws TimeoutException {
        // Create the service Intent.
        Intent serviceIntent =
                new Intent(ApplicationProvider.getApplicationContext(),
                        PhotoBurstService.class);

        // Data can be passed to the service via the Intent.
        //serviceIntent.putExtra(PhotoBurstService.SEED_KEY, 42L);

        // Bind the service and grab a reference to the binder.
        IBinder binder = mServiceRule.bindService(serviceIntent);

        // Get the reference to the service, or you can call
        // public methods on the binder directly.
        PhotoBurstService service =
                ((PhotoBurstService.LocalBinder) binder).getService();

        // Verify that the service is working correctly.
        //assertThat(service.getCount()).isAssignableTo(Integer.class);

        assertEquals(true, isMyServiceRunning(PhotoBurstService.class));
        service.stopBurst();
        service.stopForeground(true);
        service.stopSelf();
        assertEquals(false, isMyServiceRunning(PhotoBurstService.class));
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