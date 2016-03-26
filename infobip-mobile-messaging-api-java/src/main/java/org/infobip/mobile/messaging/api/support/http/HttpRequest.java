package org.infobip.mobile.messaging.api.support.http;

import org.infobip.mobile.messaging.api.support.http.client.HttpMethod;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Make a HTTP request.
 */
@Documented
@Target(METHOD)
@Retention(RUNTIME)
public @interface HttpRequest {

    HttpMethod method() default HttpMethod.GET;

    /**
     * A relative or absolute path, or full URL of the endpoint. This value is optional if the first
     * parameter of the method is annotated with {@link Url @Url}.
     */
    String value() default "";
}