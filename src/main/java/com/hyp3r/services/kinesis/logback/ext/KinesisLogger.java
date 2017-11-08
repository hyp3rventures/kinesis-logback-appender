package com.hyp3r.services.kinesis.logback.ext;

import ch.qos.logback.classic.Level;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.ext.LoggerWrapper;

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

    public void kDebug(String eventType, Map<String, String> mdc, String fmt, Object... args) {
        kDebug(eventType, null, mdc, fmt, args);
    }
    public void kDebug(String eventType, String context, Map<String, String> mdc, String fmt, Object... args) {
        kLevel(Level.DEBUG, eventType, context, mdc, fmt, null, args);
    }

    public void kInfo(String eventType, String fmt, Object... args) {
        kInfo(eventType, null, fmt, args);
    }

    public void kInfo(String eventType, Map<String, String> mdc, String fmt, Object... args) {
        kInfo(eventType, null, mdc, fmt, args);
    }

    public void kInfo(String eventType, String context, Map<String, String> mdc, String fmt, Object... args) {
        kLevel(Level.INFO, eventType, context, mdc, fmt, null, args);
    }

    public void kWarn(String eventType, String fmt, Object... args) {
        kWarn(eventType, null, fmt, args);
    }

    public void kWarn(String eventType, Map<String, String> mdc, String fmt, Object... args) {
        kWarn(eventType, null, mdc, fmt, args);
    }

    public void kWarn(String eventType, String context, Map<String, String> mdc, String fmt, Object... args) {
        kLevel(Level.WARN, eventType, context, mdc, fmt, null, args);
    }

    public void kError(String eventType, String fmt, Object... args) {
        kError(eventType, null, fmt, args);
    }

    public void kError(String eventType, Map<String, String> mdc, String fmt, Object... args) {
        kError(eventType, null, mdc, fmt, args);
    }

    public void kError(String eventType, String context, Map<String, String> mdc, String fmt, Object... args) {
        kLevel(Level.ERROR, eventType, context, mdc, fmt, null, args);
    }

    public void kError(String eventType, String fmt, Throwable ex) {
        kError(eventType, null, fmt, ex);
    }

    public void kError(String eventType, Map<String, String> mdc, String fmt, Throwable ex) {
        kError(eventType, null, mdc, fmt, ex);
    }

    public void kError(String eventType, String context, Map<String, String> mdc, String fmt, Throwable ex) {
        kLevel(Level.ERROR, eventType, context, mdc, fmt, ex);
    }

    private void kLevel(Level level, String eventType, String context, Map<String, String> mdc, String fmt, Throwable ex, Object... args) {
        if (mdc != null) {
            for (Map.Entry<String, String> entry : mdc.entrySet()) {
                MDC.put(entry.getKey(), entry.getValue());
            }
        }

        if (StringUtils.isNotBlank(eventType)) {
            MDC.put("event_type", eventType);
        }

        if (StringUtils.isNotBlank(context)) {
            MDC.put("context", context);
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
                    MDC.put("exception", ex.getClass().getName());
                    MDC.put("exceptionMessage", ex.getMessage());
                    logger.error(fmt, ex);
                }
                break;
            default:
                logger.trace(fmt, args);
        }

        MDC.clear();
    }
}
