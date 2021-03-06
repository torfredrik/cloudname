package org.cloudname.log.archiver;

import org.cloudname.log.LogUtil;
import org.cloudname.log.pb.Timber;
import org.hamcrest.core.Is;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Unit test for Archiver.
 *
 * @author borud
 */
public class ArchiverTest {
    private static final Logger log = Logger.getLogger(ArchiverTest.class.getName());

    private static final long MEGABYTE = 1024 * 1024;
    private static final String SERVICENAME = "testservice";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    /**
     * Test initialization with existing path.
     */
    @Test
    public void testInitialization() throws Exception {
        final String logPath = temp.newFolder("test1").getAbsolutePath();
        final Archiver archiver = new Archiver(logPath, SERVICENAME, MEGABYTE);
        archiver.init();
    }

    /**
     * Test initialization with path that does not exist.
     */
    @Test
    public void testInitializationUnexist() throws Exception {
        final String logPath = temp.newFolder("test2").getAbsolutePath()
            + File.separator
            + "unexist";
        final Archiver archiver = new Archiver(logPath, SERVICENAME, MEGABYTE);
        archiver.init();
    }

    /**
     * Try to log some messages.
     */
    @Test
    public void testWithMessages() throws Exception {
        final String logPath = temp.newFolder("test3").getAbsolutePath();
        final Archiver archiver = new Archiver(logPath, SERVICENAME, MEGABYTE);
        archiver.init();

        final int count = 1000;

        final List<Timber.LogEvent> events = new ArrayList<Timber.LogEvent>(count);

        for (int i = 0; i < count; i++) {
            events.add(LogUtil.textEvent(10,
                                         "myservice",
                                         ArchiverTest.class.getName(),
                                         "some payload " + i));
        }

        long offset = 0L;
        int counter = 1;
        for (final Timber.LogEvent ev : events) {
            final WriteReport wr = archiver.handle(ev);
            Assert.assertThat(wr.getWriteCount(), Is.is(counter));
            counter++;
            Assert.assertThat(wr.getStartOffset(), Is.is(offset));
            offset = wr.getEndOffset();
        }
    }

    /**
     * Microbenchmark for comparing how much sync()'ing slows down the
     * archiver.  This is not a unit test per se -- more of a
     * convenient test for seeing how much sync() slows you down.
     *
     * We have set a timeout that should be sufficient for our
     * relatively slow build server, but which should still trigger if
     * something slows down by more than 5 times what you will see on
     * a modern iMac.
     */
    @Test (timeout = 2500)
    public void testFlushSlowdownMeasurement() throws IOException {
        final String logPath = temp.newFolder("test-speed").getAbsolutePath();
        final Archiver archiver = new Archiver(logPath, SERVICENAME, MEGABYTE);
        archiver.init();

        // Create a test message
        final Timber.LogEvent logEvent = LogUtil.textEvent(
            10,
            "myservice",
            ArchiverTest.class.getName(),
            "some payload some payload some payload some payload some payload some payload "
            + "some payload some payload some payload some payload some payload some payload "
            + "some payload some payload some payload some payload some payload some payload "
            + "some payload some payload some payload some payload some payload some payload "
            + "some payload some payload some payload some payload some payload some payload "
            + "some payload some payload some payload some payload some payload some payload "
            + "some payload some payload some payload some payload some payload some payload "
            + "some payload some payload some payload some payload some payload some payload "
            + "some payload some payload some payload some payload some payload some payload "
            + "some payload some payload some payload some payload some payload some payload "
        );

        // Number of log messages to write
        final int numIterations = 10000;

        // Warmup run.  In order for everything to be properly
        // initialized we should log some log messages first.
        {
            for (int i = 0; i < numIterations; ++i) {
                archiver.handle(logEvent);
            }
        }

        // Time without flush
        long withoutFlush = 0;
        {
            final long start = System.currentTimeMillis();
            for (int i = 0; i < numIterations; ++i) {
                archiver.handle(logEvent);
            }
            withoutFlush = System.currentTimeMillis() - start;
        }

        // Time with flush
        long withFlush = 0;
        {
            final long start = System.currentTimeMillis();
            for (int i = 0; i < numIterations; ++i) {
                archiver.handle(logEvent);
                archiver.flush();
            }
            withFlush = System.currentTimeMillis() - start;
        }

        // If the run WITH flush was quicker than the run without
        // flush we didn't do enough warmup before benchmarking.  This
        // is somewhat non-deterministic by nature since we do not
        // know when the JVM decides to optimize, but we should print
        // a warning.
        if (withFlush < withoutFlush) {
            log.warning("Microbenchmark gave meaningless result: logging with flushing faster than without flushing");
            return;
        }

        final double slowdown = ((withFlush - withoutFlush) * 100) / withoutFlush;
        log.info("Slowdown for sync: " + slowdown + "%"
                 + ", withoutFlush = " + withoutFlush + "ms"
                 + ", withFlush = " + withFlush + "ms");
    }
}
