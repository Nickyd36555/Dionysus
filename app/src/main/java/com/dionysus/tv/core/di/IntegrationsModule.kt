package com.dionysus.tv.core.di

import com.dionysus.tv.data.debrid.DebridService
import com.dionysus.tv.data.debrid.premiumize.PremiumizeService
import com.dionysus.tv.data.debrid.realdebrid.RealDebridService
import com.dionysus.tv.data.scraper.Scraper
import com.dionysus.tv.data.scraper.orion.OrionScraper
import com.dionysus.tv.data.scraper.torrentio.TorrentioScraper
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Registers every scraper and debrid provider into a multibound Set. To add a
 * new integration, implement the interface and add one @Binds line here — the
 * aggregating repositories pick it up automatically.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class IntegrationsModule {

    @Binds @IntoSet abstract fun bindTorrentio(impl: TorrentioScraper): Scraper
    @Binds @IntoSet abstract fun bindOrion(impl: OrionScraper): Scraper

    @Binds @IntoSet abstract fun bindRealDebrid(impl: RealDebridService): DebridService
    @Binds @IntoSet abstract fun bindPremiumize(impl: PremiumizeService): DebridService
}
