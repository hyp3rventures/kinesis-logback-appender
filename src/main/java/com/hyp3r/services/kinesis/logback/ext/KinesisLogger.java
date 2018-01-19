package com.hyp3r.services.kinesis.logback.ext;

import ch.qos.logback.classic.Level;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.ext.LoggerWrapper;

import java.io.Closeable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

public class KinesisLogger extends LoggerWrapper implements Logger {
    public KinesisLogger(Logger logger) {
        super(logger, LoggerWrapper.class.getName());
    }

    public void kTrace(String eventType, String fmt, Object... args) {
        kLevel(Level.TRACE, eventType, null, null, fmt, null, args);
    }

    public void kDebug(String eventType, String fmt, Object... args) {
        kDebug(eventType, null, fmt, args);
    }

    public void kDebug(String eventType, Map<String, Object> mdc, String fmt, Object... args) {
        kDebug(eventType, null, mdc, fmt, args);
    }
    public void kDebug(String eventType, String context, Map<String, Object> mdc, String fmt, Object... args) {
        kLevel(Level.DEBUG, eventType, context, mdc, fmt, null, args);
    }

    public void kInfo(String eventType, String fmt, Object... args) {
        kInfo(eventType, null, fmt, args);
    }

    public void kInfo(String eventType, Map<String, Object> mdc, String fmt, Object... args) {
        kInfo(eventType, null, mdc, fmt, args);
    }

    public void kInfo(String eventType, String context, Map<String, Object> mdc, String fmt, Object... args) {
        kLevel(Level.INFO, eventType, context, mdc, fmt, null, args);
    }

    public void kWarn(String eventType, String fmt, Object... args) {
        kWarn(eventType, null, fmt, args);
    }

    public void kWarn(String eventType, Map<String, Object> mdc, String fmt, Object... args) {
        kWarn(eventType, null, mdc, fmt, args);
    }

    public void kWarn(String eventType, String context, Map<String, Object> mdc, String fmt, Object... args) {
        kLevel(Level.WARN, eventType, context, mdc, fmt, null, args);
    }

    public void kError(String eventType, String fmt, Object... args) {
        kError(eventType, null, fmt, args);
    }

    public void kError(String eventType, Map<String, Object> mdc, String fmt, Object... args) {
        kError(eventType, null, mdc, fmt, args);
    }

    public void kError(String eventType, String context, Map<String, Object> mdc, String fmt, Object... args) {
        kLevel(Level.ERROR, eventType, context, mdc, fmt, null, args);
    }

    public void kError(String eventType, String fmt, Throwable ex) {
        kError(eventType, null, fmt, ex);
    }

    public void kError(String eventType, Map<String, Object> mdc, String fmt, Throwable ex) {
        kError(eventType, null, mdc, fmt, ex);
    }

    public void kError(String eventType, String context, Map<String, Object> mdc, String fmt, Throwable ex) {
        kLevel(Level.ERROR, eventType, context, mdc, fmt, ex);
    }

    public static class MetadataBinding implements AutoCloseable {
        private final MDC.MDCCloseable underlying;
        private MetadataBinding(MDC.MDCCloseable mdcCloseable) {
            underlying = mdcCloseable;
        }
        @Override
        public void close() {
            underlying.close();
        }
    }

    public MetadataBinding bindMetadata(String key, Object val) {
        return new MetadataBinding(MDC.putCloseable(key, formatValue(val)));
    }

    public static class EventTimer implements AutoCloseable {
        private final KinesisLogger logger;
        private final String eventType;
        private final String context;
        private final long startMillis;
        private boolean stopped = false;
        private EventTimer(KinesisLogger logger, String eventType, String context) {
            this.logger = logger;
            this.eventType = eventType;
            this.context = context;
            this.startMillis = System.currentTimeMillis();
        }

        @Override
        public void close() {
            stop();
        }

        public void stop() {
            if (stopped) return;
            stopped = true;
            long endMillis = System.currentTimeMillis();
            logger.kInfo(eventType, context, Collections.singletonMap("took_millis", endMillis-startMillis), "");
        }
    }

    public EventTimer timer(String eventType, String context) {
        return new EventTimer(this, eventType, context);
    }

    public EventTimer timer(String eventType) {
        return timer(eventType, null);
    }

    private static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private String formatValue(Object val) {
        if (val instanceof Date) return dateFormat.format((Date) val);
        return val.toString();
    }

    private void kLevel(Level level, String eventType, String context, Map<String, Object> mdc, String fmt, Throwable ex, Object... args) {
        ArrayList<MetadataBinding> boundMetadata = new ArrayList<>();
        if (mdc != null) {
            for (Map.Entry<String, Object> entry : mdc.entrySet()) {
                boundMetadata.add(bindMetadata(entry.getKey(), entry.getValue()));
            }
        }

        if (StringUtils.isNotBlank(eventType)) {
            boundMetadata.add(bindMetadata("event_type", eventType));
        }

        if (StringUtils.isNotBlank(context)) {
            boundMetadata.add(bindMetadata("context", context));
        }
        switch (level.toInt()) {
            case Level.DEBUG_INT:
                logger.debug(fmt, args);
                break;
            case Level.INFO_INT:
                logger.info(fmt, args);
                break;
            case Level.WARN_INT:
                logger.warn(fmt, args);
                break;
            case Level.ERROR_INT:
                if (ex == null) {
                    logger.error(fmt, args);
                } else {
                    boundMetadata.add(bindMetadata("exception", ex.getClass().getName()));
                    boundMetadata.add(bindMetadata("exceptionMessage", ex.getMessage()));
                    logger.error(fmt, ex);
                }
                break;
            default:
                logger.trace(fmt, args);
        }

        for (MetadataBinding closeable : boundMetadata) {
            closeable.close();
        }
    }
}
