/**
 * 
 */
package org.springframework.adam.common.bean.annotation.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author user
 *
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ServiceFailRetryTimes {
	int server() default 1;

	int success() default 1;

	int fail() default 1;

	int complate() default 1;

	int sleep() default 0;

	boolean log() default true;
}
