package com.hyp3r.services.kinesis.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.status.Status;
import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.amazonaws.services.kinesis.producer.UserRecordResult;
import com.google.common.util.concurrent.Futures;
import com.hyp3r.services.kinesis.logback.ext.KinesisLogger;
import com.hyp3r.services.kinesis.logback.ext.KinesisLoggerFactory;
import com.hyp3r.services.kinesis.logback.ext.KinesisLoggerTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class KinesisAppenderTest {

    private static final KinesisLogger LOGGER = KinesisLoggerFactory.getLogger(KinesisLoggerTest.class);

    @Mock
    private KinesisProducer kinesisProducer;

    @Captor
    private ArgumentCaptor<String> captoStreamName;

    @Captor
    private ArgumentCaptor<String> captorUuid;

    @Captor
    private ArgumentCaptor<ByteBuffer> captorByteBuffer;


    @Test
    public void shouldFailToStartIfInvalidConfig() {
        KinesisAppender appender = new KinesisAppender();
        appender.start();
        assertFalse(appender.isStarted());

        appender = new KinesisAppender();
        appender.setAppName("myApp");
        appender.start();
        assertFalse(appender.isStarted());

        appender = new KinesisAppender();
        appender.setAppName("myApp");
        appender.setEnvironment("test");
        appender.start();
        assertFalse(appender.isStarted());

        appender = new KinesisAppender();
        appender.setAppName("myApp");
        appender.setEnvironment("test");
        appender.setStreamName("my-stream");
        appender.start();
        assertFalse(appender.isStarted());

        appender = new KinesisAppender();
        appender.setAppName("myApp");
        appender.setEnvironment("test");
        appender.setStreamName("my stream");
        appender.start();
        assertFalse(appender.isStarted());

        appender = new KinesisAppender();
        appender.setAppName("myApp");
        appender.setEnvironment("test");
        appender.setStreamName("my-stream");
        appender.setAwsRegion("aws-region");
        appender.start();
        assertFalse(appender.isStarted());
    }

    @Test
    public void shouldStartIfValidConfig() {
        KinesisAppender appender = new KinesisAppender();
        appender.setAppName("myApp");
        appender.setEnvironment("test");
        appender.setStreamName("my-stream");
        appender.setAwsRegion("us-east-1");
        appender.start();
        assertTrue(appender.isStarted());
    }

    @Test
    public void shouldNotAppendIfInvalid() {
        KinesisAppender<ILoggingEvent> appender = new KinesisAppender<>();
        appender.setContext(new ContextBase());
        appender.setKinesisProducer(kinesisProducer);
        appender.start();
        assertFalse(appender.isStarted());

        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.setLevel(Level.TRACE);
        logger.addAppender(appender);

        LOGGER.kDebug("event_type", "message");
        List<Status> statusList = appender.getStatusManager().getCopyOfStatusList();
        String appendError = statusList.stream()
            .filter(s -> s.getLevel() >= Status.WARN)
            .map(Status::getMessage)
            .filter(s -> s.startsWith("Attempted to append to non started appender"))
            .findFirst().orElse(null);
        assertNotNull(appendError);

        verify(kinesisProducer, never()).addUserRecord(captoStreamName.capture(), captorUuid.capture(), captorByteBuffer.capture());
    }

    @Test
    public void shouldNotAppendIfEventTypeMissing() {
        KinesisAppender<ILoggingEvent> appender = new KinesisAppender<>();
        appender.setContext(new ContextBase());
        appender.setAppName("app");
        appender.setEnvironment("env");
        appender.setEventsOnly(true);
        appender.setStreamName("stream");
        appender.setAwsRegion("us-east-1");
        appender.setKinesisProducer(kinesisProducer);
        appender.start();
        assertTrue(appender.isStarted());

        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.addAppender(appender);

        LOGGER.kDebug(null, "message");
        verify(kinesisProducer, never()).addUserRecord(captoStreamName.capture(), captorUuid.capture(), captorByteBuffer.capture());
    }

    @Test
    public void shouldCatchEncodingException() {
        KinesisAppender<ILoggingEvent> appender = new KinesisAppender<>();
        appender.setContext(new ContextBase());
        appender.setAppName("app");
        appender.setEnvironment("env");
        appender.setEventsOnly(true);
        appender.setStreamName("stream");
        appender.setAwsRegion("us-east-1");
        appender.setKinesisProducer(kinesisProducer);
        appender.start();
        assertTrue(appender.isStarted());

        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.addAppender(appender);

        when(kinesisProducer.addUserRecord(anyString(), anyString(), any())).thenThrow(new RuntimeException());

        LOGGER.kDebug("event_type", "message");
        verify(kinesisProducer).addUserRecord(captoStreamName.capture(), captorUuid.capture(), captorByteBuffer.capture());

        List<Status> statusList = appender.getStatusManager().getCopyOfStatusList();
        String appendError = statusList.stream()
            .filter(s -> s.getLevel() >= Status.ERROR)
            .map(Status::getMessage)
            .filter(s -> s.startsWith("Failed to send event to kinesis"))
            .findFirst().orElse(null);
        assertNotNull(appendError);

        reset(kinesisProducer);
    }

    @Test
    public void shouldExecuteSuccessCallback() throws ExecutionException, InterruptedException {
        KinesisAppender<ILoggingEvent> appender = new KinesisAppender<>();
        appender.setContext(new ContextBase());
        appender.setAppName("app");
        appender.setEnvironment("env");
        appender.setEventsOnly(true);
        appender.setStreamName("stream");
        appender.setAwsRegion("us-east-1");
        appender.setKinesisProducer(kinesisProducer);
        appender.start();
        assertTrue(appender.isStarted());

        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.addAppender(appender);

        when(kinesisProducer.addUserRecord(anyString(), anyString(), any())).thenReturn(Futures.immediateFuture(mock(UserRecordResult.class)));

        LOGGER.kDebug("event_type", "message");
        verify(kinesisProducer).addUserRecord(captoStreamName.capture(), captorUuid.capture(), captorByteBuffer.capture());

        reset(kinesisProducer);
    }

    @Test
    public void shouldExecuteFailureCallback() throws ExecutionException, InterruptedException {
        KinesisAppender<ILoggingEvent> appender = new KinesisAppender<>();
        appender.setContext(new ContextBase());
        appender.setAppName("app");
        appender.setEnvironment("env");
        appender.setEventsOnly(true);
        appender.setStreamName("stream");
        appender.setAwsRegion("us-east-1");
        appender.setKinesisProducer(kinesisProducer);
        appender.start();
        assertTrue(appender.isStarted());

        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.addAppender(appender);

        when(kinesisProducer.addUserRecord(anyString(), anyString(), any())).thenReturn(Futures.immediateFailedFuture(new RuntimeException()));

        LOGGER.kDebug("event_type", "message");
        verify(kinesisProducer).addUserRecord(captoStreamName.capture(), captorUuid.capture(), captorByteBuffer.capture());

        reset(kinesisProducer);
    }

    @Test
    public void shouldStop() {
        KinesisAppender appender = new KinesisAppender();
        appender.setAppName("myApp");
        appender.setEnvironment("test");
        appender.setEventsOnly(false);
        appender.setStreamName("my-stream");
        appender.setAwsRegion("us-east-1");
        appender.start();
        assertTrue(appender.isStarted());

        appender.stop();
        assertFalse(appender.isStarted());

        appender = new KinesisAppender();
        appender.start();
        assertFalse(appender.isStarted());

        appender.stop();
        assertFalse(appender.isStarted());
    }
}
