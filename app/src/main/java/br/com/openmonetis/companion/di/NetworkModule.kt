package br.com.openmonetis.companion.di

import br.com.openmonetis.companion.data.remote.OpenMonetisApi
import br.com.openmonetis.companion.data.remote.interceptors.AuthInterceptor
import br.com.openmonetis.companion.data.remote.interceptors.DynamicUrlInterceptor
import br.com.openmonetis.companion.util.SecureStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Marks the plain HTTP client used to fetch third-party pages (e.g. the Sefaz
 * NFC-e portals). It must never carry the OpenMonetis auth token or the
 * dynamic base-URL rewrite meant for the user's own backend.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ExternalHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideDynamicUrlInterceptor(
        secureStorage: SecureStorage
    ): DynamicUrlInterceptor {
        return DynamicUrlInterceptor(secureStorage)
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        secureStorage: SecureStorage
    ): AuthInterceptor {
        return AuthInterceptor(secureStorage)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        dynamicUrlInterceptor: DynamicUrlInterceptor,
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(dynamicUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @ExternalHttpClient
    fun provideExternalHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient
    ): Retrofit {
        // Base URL is a placeholder - will be replaced by DynamicUrlInterceptor
        return Retrofit.Builder()
            .baseUrl("https://placeholder.local/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenMonetisApi(retrofit: Retrofit): OpenMonetisApi {
        return retrofit.create(OpenMonetisApi::class.java)
    }
}
