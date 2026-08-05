package com.dionysus.tv.core.di

import javax.inject.Qualifier

/** OkHttp client pre-configured with Real-Debrid auth + token refresh. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RealDebridClient

/** OkHttp client that injects the Anthropic API key + version headers. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AnthropicClient
