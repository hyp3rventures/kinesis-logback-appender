package com.hyp3r.services.kinesis.logback.ext;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hyp3r.services.kinesis.logback.KinesisAppender;
import com.hyp3r.services.kinesis.logback.models.KinesisLogEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class KinesisLoggerTest {

    private static final KinesisLogger LOGGER = KinesisLoggerFactory.getLogger(KinesisLoggerTest.class);
    private static final Gson GSON = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").create();

    // Test constants
    private static final String MY_STREAM = "my-stream";
    private static final String MY_APP = "myApp";
    private static final String ENV = "test";
    private static final String EVENT_TYPE = "event-type";
    private static final String CONTEXT = "context";
    private static final String MSG = "this is a debug message fmt {}";
    private static final String ARG = "FMT";
    private static final String FORMATTED_MESSAGE = MessageFormatter.arrayFormat(MSG, new Object[]{ARG}).getMessage();
    private static final HashMap<String, Object> MDC = new HashMap<>();
    private static final String EXCEPTION_MSG = "This is my exception message";

    static {
        MDC.put("key", "value");
    }

    @Mock
    private KinesisProducer kinesisProducer;

    @Captor
    private ArgumentCaptor<String> captoStreamName;

    @Captor
    private ArgumentCaptor<String> captorUuid;

    @Captor
    private ArgumentCaptor<ByteBuffer> captorByteBuffer;

    private KinesisAppender<ILoggingEvent> appender;

    @Before
    public void setup() {
        appender = new KinesisAppender<>();
        appender.setAppName(MY_APP);
        appender.setEnvironment(ENV);
        appender.setEventsOnly(true);
        appender.setStreamName(MY_STREAM);
        appender.setAwsRegion("us-east-1");
        appender.setKinesisProducer(kinesisProducer);
        appender.start();

        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.setLevel(Level.TRACE);
        logger.addAppender(appender);
    }

    @After
    public void teardown() {
        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.detachAppender(appender);
    }

    @Test
    public void shouldCreateKinesisLoggerFactory() {
        new KinesisLoggerFactory();
    }

    private static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    @Test
    public void logWithBoundMetadata() throws Exception {
        Throwable myThrowable = new IllegalArgumentException("SBH!");
        KinesisLogger.MetadataBinding server = LOGGER.bindMetadata("server", "hyp3r.org");
        try (KinesisLogger.MetadataBinding k1 = LOGGER.bindMetadata("k1", "v1");
             KinesisLogger.MetadataBinding k2 = LOGGER.bindMetadata("k2", null);
             KinesisLogger.MetadataBinding ts = LOGGER.bindMetadata("ts", new Date())) {
            LOGGER.kInfo("my_event", "This is the note for my event");
        }
        LOGGER.kError("my_error", "Something bad happened.", myThrowable);
        verify(kinesisProducer, times(2)).addUserRecord(captoStreamName.capture(), captorUuid.capture(), captorByteBuffer.capture());
        KinesisLogEvent logEvent = getLogEvent(captorByteBuffer.getAllValues().get(0));
        Map<String, String> metadata = logEvent.getMetadata();
        assertNotNull(dateFormat.parse(metadata.get("ts")));
        assertTrue(metadata.containsKey("k1"));
        assertTrue(metadata.containsKey("k2"));
        assertTrue(metadata.containsKey("server"));

        logEvent = getLogEvent(captorByteBuffer.getAllValues().get(1));
        metadata = logEvent.getMetadata();
        assertFalse(metadata.containsKey("ts"));
        assertFalse(metadata.containsKey("k1"));
        assertFalse(metadata.containsKey("k2"));
        assertTrue(metadata.containsKey("server"));

        List<ByteBuffer> jsons = captorByteBuffer.getAllValues();
        String json = new String(jsons.get(0).array());
        System.out.println(json);
        json = new String(jsons.get(1).array());
        System.out.println(json);
        server.close();
    }

    @Test
    public void logWithConciselyBoundMetadata() throws Exception {
        Throwable myThrowable = new IllegalArgumentException("SBH!");
        KinesisLogger.MetadataBinding server = LOGGER.bindMetadata("server", "hyp3r.org");
        try (KinesisLogger.MetadataBinding k1 = LOGGER.bindMetadata("k1", "v1")
                                                               .and("k2", "v2")
                                                               .and("ts", new Date())) {
            LOGGER.kInfo("my_event", "This is the note for my event");
        }
        LOGGER.kError("my_error", "Something bad happened.", myThrowable);
        verify(kinesisProducer, times(2)).addUserRecord(captoStreamName.capture(), captorUuid.capture(), captorByteBuffer.capture());
        KinesisLogEvent logEvent = getLogEvent(captorByteBuffer.getAllValues().get(0));
        Map<String, String> metadata = logEvent.getMetadata();
        assertNotNull(dateFormat.parse(metadata.get("ts")));
        assertTrue(metadata.containsKey("k1"));
        assertTrue(metadata.containsKey("k2"));
        assertTrue(metadata.containsKey("server"));

        logEvent = getLogEvent(captorByteBuffer.getAllValues().get(1));
        metadata = logEvent.getMetadata();
        assertFalse(metadata.containsKey("ts"));
        assertFalse(metadata.containsKey("k1"));
        assertFalse(metadata.containsKey("k2"));
        assertTrue(metadata.containsKey("server"));

        List<ByteBuffer> jsons = captorByteBuffer.getAllValues();
        String json = new String(jsons.get(0).array());
        System.out.println(json);
        json = new String(jsons.get(1).array());
        System.out.println(json);
        server.close();
    }



    @Test
    public void logWithGlobals() throws Exception {
        Throwable myThrowable = new IllegalArgumentException("SBH!");
        KinesisLogger.addGlobalMetadata("server", "hyp3r.org");
        try (KinesisLogger.MetadataBinding k1 = LOGGER.bindMetadata("k1", "v1")
                                                               .and("k2", "v2")
                                                               .and("server", "tmpserver")
                                                               .and("ts", new Date())) {
            LOGGER.kInfo("my_event", "This is the note for my event");
        }
        LOGGER.kError("my_error", "Something bad happened.", myThrowable);
        verify(kinesisProducer, times(2)).addUserRecord(captoStreamName.capture(), captorUuid.capture(), captorByteBuffer.capture());
        KinesisLogEvent logEvent = getLogEvent(captorByteBuffer.getAllValues().get(0));
        Map<String, String> metadata = logEvent.getMetadata();
        assertNotNull(dateFormat.parse(metadata.get("ts")));
        assertTrue(metadata.containsKey("k1"));
        assertTrue(metadata.containsKey("k2"));
        assertTrue(metadata.get("server").equals("tmpserver"));

        logEvent = getLogEvent(captorByteBuffer.getAllValues().get(1));
        metadata = logEvent.getMetadata();
        assertFalse(metadata.containsKey("ts"));
        assertFalse(metadata.containsKey("k1"));
        assertFalse(metadata.containsKey("k2"));
        assertTrue(metadata.get("server").equals("hyp3r.org"));

        List<ByteBuffer> jsons = captorByteBuffer.getAllValues();
        String json = new String(jsons.get(0).array());
        System.out.println(json);
        json = new String(jsons.get(1).array());
        System.out.println(json);
        KinesisLogger.clearGlobalMetadata();
    }




    @Test
    public void logWithEventTimer() throws Exception {
        try (KinesisLogger.EventTimer timer =  LOGGER.timer("my_timed_event")) {
            Thread.sleep(100);
            timer.stop(); //Extra, superfluous stop to test idempotence.
        }
        verify(kinesisProducer).addUserRecord(captoStreamName.capture(), captorUuid.capture(), captorByteBuffer.capture());
        List<ByteBuffer> jsons = captorByteBuffer.getAllValues();

        String json = new String(jsons.get(0).array());
        System.out.println(json);
    }

    @Test
    public void shouldLogNonEventWhenEventsOnlyFalse() {
        appender.setEventsOnly(false);
        LOGGER.kTrace(null, "null event");

        verify(kinesisProducer).addUserRecord(captoStreamName.capture(), captorUuid.capture(), captorByteBuffer.capture());
        KinesisLogEvent logEvent = getLogEvent(captorByteBuffer.getValue());
        assertEquals(MY_STREAM, captoStreamName.getValue());
        assertEquals(MY_APP, logEvent.getAppName());
        assertEquals(ENV, logEvent.getEnvironment());
        assertEquals(Level.TRACE.levelStr, logEvent.getLevel());
        assertEquals(KinesisLoggerTest.class.getName(), logEvent.getLoggerName());
        assertNull(logEvent.getEventType());
        assertNull(logEvent.getContext());
        assertEquals("null event", logEvent.getDescription());
        assertNull(logEvent.getStacktrace());
        assertNotNull(logEvent.getTimestamp());
        assertTrue(logEvent.getMetadata().isEmpty());

        appender.setEventsOnly(true);
    }

    @Test
    public void shouldLogKDebugToKinesis() {
        LOGGER.kDebug(EVENT_TYPE, MSG);
        LOGGER.kDebug(EVENT_TYPE, MSG, ARG);
        LOGGER.kDebug(EVENT_TYPE, MDC, MSG, ARG);
        LOGGER.kDebug(EVENT_TYPE, CONTEXT, MDC, MSG, ARG);


        verify(kinesisProducer, times(4)).addUserRecord(captoStreamName.capture(), captorUuid.capture(), captorByteBuffer.capture());
        List<String> capturedStreams = captoStreamName.getAllValues();
        List<ByteBuffer> byteBuffers = captorByteBuffer.getAllValues();

        KinesisLogEvent logEvent = getLogEvent(byteBuffers.get(0));
        assertEquals(MY_STREAM, capturedStreams.get(0));
        assertEquals(MY_APP, logEvent.getAppName());
        assertEquals(ENV, logEvent.getEnvironment());
        assertEquals(Level.DEBUG.levelStr, logEvent.getLevel());
        assertEquals(KinesisLoggerTest.class.getName(), logEvent.getLoggerName());
        assertEquals(EVENT_TYPE, logEvent.getEventType());
        assertNull(logEvent.getContext());
        assertEquals(MSG, logEvent.getDescription());
        assertNull(logEvent.getStacktrace());
        assertNotNull(logEvent.getTimestamp());
        assertTrue(logEvent.getMetadata().isEmpty());

        logEvent = getLogEvent(byteBuffers.get(1));
        assertEquals(MY_STREAM, capturedStreams.get(1));
        assertEquals(MY_APP, logEvent.getAppName());
        assertEquals(ENV, logEvent.getEnvironment());
        assertEquals(Level.DEBUG.levelStr, logEvent.getLevel());
        assertEquals(KinesisLoggerTest.class.getName(), logEvent.getLoggerName());
        assertEquals(EVENT_TYPE, logEvent.getEventType());
        assertNull(logEvent.getContext());
        assertEquals(FORMATTED_MESSAGE, logEvent.getDescription());
        assertNull(logEvent.getStacktrace());
        assertNotNull(logEvent.getTimestamp());
        assertTrue(logEvent.getMetadata().isEmpty());

        logEvent = getLogEvent(byteBuffers.get(2));
        assertEquals(MY_STREAM, capturedStreams.get(2));
        assertEquals(MY_APP, logEvent.getAppName());
        assertEquals(ENV, logEvent.getEnvironment());
        assertEquals(Level.DEBUG.levelStr, logEvent.getLevel());
        assertEquals(KinesisLoggerTest.class.getName(), logEvent.getLoggerName());
        assertEquals(EVENT_TYPE, logEvent.getEventType());
        assertNull(logEvent.getContext());
        assertEquals(FORMATTED_MESSAGE, logEvent.getDescription());
        assertNull(logEvent.getStacktrace());
        assertNotNull(logEvent.getTimestamp());
        assertTrue(logEvent.getMetadata().containsKey("key"));
        assertEquals("value", logEvent.getMetadata().get("key"));

        logEvent = getLogEvent(byteBuffers.get(3));
        assertEquals(MY_STREAM, capturedStreams.get(3));
        assertEquals(MY_APP, logEvent.getAppName());
        assertEquals(ENV, logEvent.getEnvironment());
        assertEquals(Level.DEBUG.levelStr, logEvent.getLevel());
        assertEquals(KinesisLoggerTest.class.getName(), logEvent.getLoggerName());
        assertEquals(EVENT_TYPE, logEvent.getEventType());
        assertEquals(CONTEXT, logEvent.getContext());
        assertEquals(FORMATTED_MESSAGE, logEvent.getDescription());
        assertNull(logEvent.getStacktrace());
        assertNotNull(logEvent.getTimestamp());
        assertTrue(logEvent.getMetadata().containsKey("key"));
        assertEquals("value", logEvent.getMetadata().get("key"));
    }

    @Test
    public void shouldLogKInfoToKinesis() {
        LOGGER.kInfo(EVENT_TYPE, MSG);
        LOGGER.kInfo(EVENT_TYPE, MSG, ARG);
        LOGGER.kInfo(EVENT_TYPE, MDC, MSG, ARG);
        LOGGER.kInfo(EVENT_TYPE, CONTEXT, MDC, MSG, ARG);

        verify(kinesisProducer, times(4)).addUserRecord(captoStreamName.capture(), captorUuid.capture(), captorByteBuffer.capture());
        List<String> capturedStreams = captoStreamName.getAllValues();
        List<ByteBuffer> byteBuffers = captorByteBuffer.getAllValues();

        KinesisLogEvent logEvent = getLogEvent(byteBuffers.get(0));
        assertEquals(MY_STREAM, capturedStreams.get(0));
        assertEquals(MY_APP, logEvent.getAppName());
        assertEquals(ENV, logEvent.getEnvironment());
        assertEquals(Level.INFO.levelStr, logEvent.getLevel());
        assertEquals(KinesisLoggerTest.class.getName(), logEvent.getLoggerName());
        assertEquals(EVENT_TYPE, logEvent.getEventType());
        assertNull(logEvent.getContext());
        assertEquals(MSG, logEvent.getDescription());
        assertNull(logEvent.getStacktrace());
        assertNotNull(logEvent.getTimestamp());
        assertTrue(logEvent.getMetadata().isEmpty());

        logEvent = getLogEvent(byteBuffers.get(1));
        assertEquals(MY_STREAM, capturedStreams.get(1));
        assertEquals(MY_APP, logEvent.getAppName());
        assertEquals(ENV, logEvent.getEnvironment());
        assertEquals(Level.INFO.levelStr, logEvent.getLevel());
        assertEquals(KinesisLoggerTest.class.getName(), logEvent.getLoggerName());
        assertEquals(EVENT_TYPE, logEvent.getEventType());
        assertNull(logEvent.getContext());
        assertEquals(FORMATTED_MESSAGE, logEvent.getDescription());
        assertNull(logEvent.getStacktrace());
        assertNotNull(logEvent.getTimestamp());
        assertTrue(logEvent.getMetadata().isEmpty());

        logEvent = getLogEvent(byteBuffers.get(2));
        assertEquals(MY_STREAM, capturedStreams.get(2));
        assertEquals(MY_APP, logEvent.getAppName());
        assertEquals(ENV, logEvent.getEnvironment());
        assertEquals(Level.INFO.levelStr, logEvent.getLevel());
        assertEquals(KinesisLoggerTest.class.getName(), logEvent.getLoggerName());
        assertEquals(EVENT_TYPE, logEvent.getEventType());
        assertNull(logEvent.getContext());
        assertEquals(FORMATTED_MESSAGE, logEvent.getDescription());
        assertNull(logEvent.getStacktrace());
        assertNotNull(logEvent.getTimestamp());
        assertTrue(logEvent.getMetadata().containsKey("key"));
        assertEquals("value", logEvent.getMetadata().get("key"));

        logEvent = getLogEvent(byteBuffers.get(3));
        assertEquals(MY_STREAM, capturedStreams.get(3));
        assertEquals(MY_APP, logEvent.getAppName());
        assertEquals(ENV, logEvent.getEnvironment());
        assertEquals(Level.INFO.levelStr, logEvent.getLevel());
        assertEquals(KinesisLoggerTest.class.getName(), logEvent.getLoggerName());
        assertEquals(EVENT_TYPE, logEvent.getEventType());
        assertEquals(CONTEXT, logEvent.getContext());
        assertEquals(FORMATTED_MESSAGE, logEvent.getDescription());
        assertNull(logEvent.getStacktrace());
        assertNotNull(logEvent.getTimestamp());
        assertTrue(logEvent.getMetadata().containsKey("key"));
        assertEquals("value", logEvent.getMetadata().get("key"));
    }

    @Test
    public void shouldLogKWarnToKinesis() {
        LOGGER.kWarn(EVENT_TYPE, MSG);
        LOGGER.kWarn(EVENT_TYPE, MSG, ARG);
        LOGGER.kWarn(EVENT_TYPE, MDC, MSG, ARG);
        LOGGER.kWarn(EVENT_TYPE, CONTEXT, MDC, MSG, ARG);

        verify(kinesisProducer, times(4)).addUserRecord(captoStreamName.capture(), captorUuid.capture(), captorByteBuffer.capture());
        List<String> capturedStreams = captoStreamName.getAllValues();
        List<ByteBuffer> byteBuffers = captorByteBuffer.getAllValues();

        KinesisLogEvent logEvent = getLogEvent(byteBuffers.get(0));
        assertEquals(MY_STREAM, capturedStreams.get(0));
        assertEquals(MY_APP, logEvent.getAppName());
        assertEquals(ENV, logEvent.getEnvironment());
        assertEquals(Level.WARN.levelStr, logEvent.getLevel());
        assertEquals(KinesisLoggerTest.class.getName(), logEvent.getLoggerName());
        assertEquals(EVENT_TYPE, logEvent.getEventType());
        assertNull(logEvent.getContext());
        assertEquals(MSG, logEvent.getDescription());
        assertNull(logEvent.getStacktrace());
        assertNotNull(logEvent.getTimestamp());
        assertTrue(logEvent.getMetadata().isEmpty());

        logEvent = getLogEvent(byteBuffers.get(1));
        assertEquals(MY_STREAM, capturedStreams.get(1));
        assertEquals(MY_APP, logEvent.getAppName());
        assertEquals(ENV, logEvent.getEnvironment());
        assertEquals(Level.WARN.levelStr, logEvent.getLevel());
        assertEquals(KinesisLoggerTest.class.getName(), logEvent.getLoggerName());
        assertEquals(EVENT_TYPE, logEvent.getEventType());
        assertNull(logEvent.getContext());
        assertEquals(FORMATTED_MESSAGE, logEvent.getDescription());
        assertNull(logEvent.getStacktrace());
        assertNotNull(logEvent.getTimestamp());
        assertTrue(logEvent.getMetadata().isEmpty());

        logEvent = getLogEvent(byteBuffers.get(2));
        assertEquals(MY_STREAM, capturedStreams.get(2));
        assertEquals(MY_APP, logEvent.getAppName());
        assertEquals(ENV, logEvent.getEnvironment());
        assertEquals(Level.WARN.levelStr, logEvent.getLevel());
        assertEquals(KinesisLoggerTest.class.getName(), logEvent.getLoggerName());
        assertEquals(EVENT_TYPE, logEvent.getEventType());
        assertNull(logEvent.getContext());
        assertEquals(FORMATTED_MESSAGE, logEvent.getDescription());
        assertNull(logEvent.getStacktrace());
        assertNotNull(logEvent.getTimestamp());
        assertTrue(logEvent.getMetadata().containsKey("key"));
        assertEquals("value", logEvent.getMetadata().get("key"));

        logEvent = getLogEvent(byteBuffers.get(3));
        assertEquals(MY_STREAM, capturedStreams.get(3));
        assertEquals(MY_APP, logEvent.getAppName());
        assertEquals(ENV, logEvent.getEnvironment());
        assertEquals(Level.WARN.levelStr, logEvent.getLevel());
        assertEquals(KinesisLoggerTest.class.getName(), logEvent.getLoggerName());
        assertEquals(EVENT_TYPE, logEvent.getEventType());
        assertEquals(CONTEXT, logEvent.getContext());
        assertEquals(FORMATTED_MESSAGE, logEvent.getDescription());
        assertNull(logEvent.getStacktrace());
        assertNotNull(logEvent.getTimestamp());
        assertTrue(logEvent.getMetadata().containsKey("key"));
        assertEquals("value", logEvent.getMetadata().get("key"));
    }

    @Test
    public void shouldLogKErrorToKinesis() {
        LOGGER.kError(EVENT_TYPE, MSG);
        LOGGER.kError(EVENT_TYPE, MSG, ARG);
        LOGGER.kError(EVENT_TYPE, MDC, MSG, ARG);
        LOGGER.kError(EVENT_TYPE, CONTEXT, MDC, MSG, ARG);

        try {
            throw new Exception(EXCEPTION_MSG);
        } catch (Exception e) {
            LOGGER.kError(EVENT_TYPE, MSG, e);
            LOGGER.kError(EVENT_TYPE, MDC, MSG, e);
            LOGGER.kError(EVENT_TYPE, CONTEXT, MDC, MSG, e);
        }

        verify(kinesisProducer, times(7)).addUserRecord(captoStreamName.capture(), captorUuid.capture(), captorByteBuffer.capture());
        List<String> capturedStreams = captoStreamName.getAllValues();
        List<ByteBuffer> byteBuffers = captorByteBuffer.getAllValues();

        KinesisLogEvent logEvent = getLogEvent(byteBuffers.get(0));
        assertEquals(MY_STREAM, capturedStreams.get(0));
        assertEquals(MY_APP, logEvent.getAppName());
        assertEquals(ENV, logEvent.getEnvironment());
        assertEquals(Level.ERROR.levelStr, logEvent.getLevel());
        assertEquals(KinesisLoggerTest.class.getName(), logEvent.getLoggerName());
        assertEquals(EVENT_TYPE, logEvent.getEventType());
        assertNull(logEvent.getContext());
        assertEquals(MSG, logEvent.getDescription());
        assertNull(logEvent.getStacktrace());
        assertNotNull(logEvent.getTimestamp());
        assertTrue(logEvent.getMetadata().isEmpty());

        logEvent = getLogEvent(byteBuffers.get(1));
        assertEquals(MY_STREAM, capturedStreams.get(1));
        assertEquals(MY_APP, logEvent.getAppName());
        assertEquals(ENV, logEvent.getEnvironment());
        assertEquals(Level.ERROR.levelStr, logEvent.getLevel());
        assertEquals(KinesisLoggerTest.class.getName(), logEvent.getLoggerName());
        assertEquals(EVENT_TYPE, logEvent.getEventType());
        assertNull(logEvent.getContext());
        assertEquals(FORMATTED_MESSAGE, logEvent.getDescription());
        assertNull(logEvent.getStacktrace());
        assertNotNull(logEvent.getTimestamp());
        assertTrue(logEvent.getMetadata().isEmpty());

        logEvent = getLogEvent(byteBuffers.get(2));
        assertEquals(MY_STREAM, capturedStreams.get(2));
        assertEquals(MY_APP, logEvent.getAppName());
        assertEquals(ENV, logEvent.getEnvironment());
        assertEquals(Level.ERROR.levelStr, logEvent.getLevel());
        assertEquals(KinesisLoggerTest.class.getName(), logEvent.getLoggerName());
        assertEquals(EVENT_TYPE, logEvent.getEventType());
        assertNull(logEvent.getContext());
        assertEquals(FORMATTED_MESSAGE, logEvent.getDescription());
        assertNull(logEvent.getStacktrace());
        assertNotNull(logEvent.getTimestamp());
        assertTrue(logEvent.getMetadata().containsKey("key"));
        assertEquals("value", logEvent.getMetadata().get("key"));

        logEvent = getLogEvent(byteBuffers.get(3));
        assertEquals(MY_STREAM, capturedStreams.get(3));
        assertEquals(MY_APP, logEvent.getAppName());
        assertEquals(ENV, logEvent.getEnvironment());
        assertEquals(Level.ERROR.levelStr, logEvent.getLevel());
        assertEquals(KinesisLoggerTest.class.getName(), logEvent.getLoggerName());
        assertEquals(EVENT_TYPE, logEvent.getEventType());
        assertEquals(CONTEXT, logEvent.getContext());
        assertEquals(FORMATTED_MESSAGE, logEvent.getDescription());
        assertNull(logEvent.getStacktrace());
        assertNotNull(logEvent.getTimestamp());
        assertTrue(logEvent.getMetadata().containsKey("key"));
        assertEquals("value", logEvent.getMetadata().get("key"));

        logEvent = getLogEvent(byteBuffers.get(4));
        assertEquals(MY_STREAM, capturedStreams.get(4));
        assertEquals(MY_APP, logEvent.getAppName());
        assertEquals(ENV, logEvent.getEnvironment());
        assertEquals(Level.ERROR.levelStr, logEvent.getLevel());
        assertEquals(KinesisLoggerTest.class.getName(), logEvent.getLoggerName());
        assertEquals(EVENT_TYPE, logEvent.getEventType());
        assertNull(logEvent.getContext());
        assertEquals(MSG, logEvent.getDescription());
        assertNotNull(logEvent.getStacktrace());
        assertNotNull(logEvent.getTimestamp());
        assertTrue(logEvent.getMetadata().containsKey("exception"));
        assertEquals(Exception.class.getName(), logEvent.getMetadata().get("exception"));
        assertTrue(logEvent.getMetadata().containsKey("exceptionMessage"));
        assertEquals(EXCEPTION_MSG, logEvent.getMetadata().get("exceptionMessage"));

        logEvent = getLogEvent(byteBuffers.get(5));
        assertEquals(MY_STREAM, capturedStreams.get(5));
        assertEquals(MY_APP, logEvent.getAppName());
        assertEquals(ENV, logEvent.getEnvironment());
        assertEquals(Level.ERROR.levelStr, logEvent.getLevel());
        assertEquals(KinesisLoggerTest.class.getName(), logEvent.getLoggerName());
        assertEquals(EVENT_TYPE, logEvent.getEventType());
        assertNull(logEvent.getContext());
        assertEquals(MSG, logEvent.getDescription());
        assertNotNull(logEvent.getStacktrace());
        assertNotNull(logEvent.getTimestamp());
        assertTrue(logEvent.getMetadata().containsKey("key"));
        assertEquals("value", logEvent.getMetadata().get("key"));
        assertTrue(logEvent.getMetadata().containsKey("exception"));
        assertEquals(Exception.class.getName(), logEvent.getMetadata().get("exception"));
        assertTrue(logEvent.getMetadata().containsKey("exceptionMessage"));
        assertEquals(EXCEPTION_MSG, logEvent.getMetadata().get("exceptionMessage"));

        logEvent = getLogEvent(byteBuffers.get(6));
        assertEquals(MY_STREAM, capturedStreams.get(6));
        assertEquals(MY_APP, logEvent.getAppName());
        assertEquals(ENV, logEvent.getEnvironment());
        assertEquals(Level.ERROR.levelStr, logEvent.getLevel());
        assertEquals(KinesisLoggerTest.class.getName(), logEvent.getLoggerName());
        assertEquals(EVENT_TYPE, logEvent.getEventType());
        assertEquals(CONTEXT, logEvent.getContext());
        assertEquals(MSG, logEvent.getDescription());
        assertNotNull(logEvent.getStacktrace());
        assertNotNull(logEvent.getTimestamp());
        assertTrue(logEvent.getMetadata().containsKey("key"));
        assertEquals("value", logEvent.getMetadata().get("key"));
        assertTrue(logEvent.getMetadata().containsKey("exception"));
        assertEquals(Exception.class.getName(), logEvent.getMetadata().get("exception"));
        assertTrue(logEvent.getMetadata().containsKey("exceptionMessage"));
        assertEquals(EXCEPTION_MSG, logEvent.getMetadata().get("exceptionMessage"));
    }

    private KinesisLogEvent getLogEvent(ByteBuffer byteBuffer) {
        String json = new String(byteBuffer.array());
        return GSON.fromJson(json, KinesisLogEvent.class);
    }
}
