package it.utils.nailed;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    /*
    * TODO
    * add test checking for active service/threads/timers
    * check there is online one service/timer at any given time
    * even if start burst clicked repeatedly
    * even if app quits and is started again, or started multiple times without closing it
    * */

    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertEquals("it.utils.nailed", appContext.getPackageName());
    }
}
