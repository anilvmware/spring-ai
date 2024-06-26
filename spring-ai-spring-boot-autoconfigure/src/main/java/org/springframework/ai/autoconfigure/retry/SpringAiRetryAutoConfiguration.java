/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.retry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResponseErrorHandler;

/**
 * @author Christian Tzolov
 */
@AutoConfiguration
@ConditionalOnClass(RetryTemplate.class)
@EnableConfigurationProperties({ SpringAiRetryProperties.class })
public class SpringAiRetryAutoConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(SpringAiRetryAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	public RetryTemplate retryTemplate(SpringAiRetryProperties properties) {
		return RetryTemplate.builder()
			.maxAttempts(properties.getMaxAttempts())
			.retryOn(NonTransientAiException.class)
			.exponentialBackoff(properties.getBackoff().getInitialInterval(), properties.getBackoff().getMultiplier(),
					properties.getBackoff().getMaxInterval())
			.withListener(new RetryListener() {
				@Override
				public <T extends Object, E extends Throwable> void onError(RetryContext context,
						RetryCallback<T, E> callback, Throwable throwable) {
					logger.warn("Retry error. Retry count:" + context.getRetryCount(), throwable);
				};
			})
			.build();
	}

	@Bean
	@ConditionalOnMissingBean
	public ResponseErrorHandler responseErrorHandler(SpringAiRetryProperties properties) {

		return new ResponseErrorHandler() {

			@Override
			public boolean hasError(@NonNull ClientHttpResponse response) throws IOException {
				return response.getStatusCode().isError();
			}

			@Override
			public void handleError(@NonNull ClientHttpResponse response) throws IOException {
				if (response.getStatusCode().isError()) {
					String error = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
					String message = String.format("%s - %s", response.getStatusCode().value(), error);
					/**
					 * Thrown on 4xx client errors, such as 401 - Incorrect API key
					 * provided, 401 - You must be a member of an organization to use the
					 * API, 429 - Rate limit reached for requests, 429 - You exceeded your
					 * current quota , please check your plan and billing details.
					 */
					// onClientErrors - If true, do not throw a NonTransientAiException,
					// and do not attempt retry for 4xx client error codes, false by
					// default.
					if (!properties.isOnClientErrors() && response.getStatusCode().is4xxClientError()) {
						throw new NonTransientAiException(message);
					}
					// Explicitly configured non-transient codes
					if (!CollectionUtils.isEmpty(properties.getExcludeOnHttpCodes())
							&& properties.getExcludeOnHttpCodes().contains(response.getStatusCode().value())) {
						throw new NonTransientAiException(message);
					}
					throw new TransientAiException(message);
				}
			}
		};
	}

}
