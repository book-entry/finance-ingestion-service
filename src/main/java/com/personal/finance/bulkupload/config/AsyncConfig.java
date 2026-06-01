package com.personal.finance.bulkupload.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;

/**
 * Flattens stack traces into a single log line so they survive Zeabur's
 * CSV log export (and any other one-line-per-event aggregator). Default
 * SimpleAsyncUncaughtExceptionHandler emits the stack on subsequent lines,
 * which get truncated.
 */
@Configuration
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            String flat = sw.toString().replace('\n', '|').replace('\r', ' ');
            log.error("Async method failed: class={} method={} params={} | {}",
                    method.getDeclaringClass().getSimpleName(),
                    method.getName(),
                    Arrays.toString(params),
                    flat);
        };
    }
}
