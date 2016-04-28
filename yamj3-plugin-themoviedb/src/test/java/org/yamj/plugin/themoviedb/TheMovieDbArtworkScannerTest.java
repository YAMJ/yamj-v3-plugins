/*
 *      Copyright (c) 2004-2016 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ) plugins.
 *
 *      YAMJ is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation, either version 3 of the License, or
 *      any later version.
 *
 *      YAMJ is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: https://github.com/YAMJ/yamj-v3-plugins
 *
 */
package org.yamj.plugin.themoviedb;

import static org.yamj.plugin.api.Constants.SOURCE_TMDB;

import java.util.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.http.CommonHttpClient;
import org.yamj.api.common.http.HttpClientWrapper;
import org.yamj.api.common.http.SimpleHttpClientBuilder;
import org.yamj.plugin.api.artwork.ArtworkDTO;
import org.yamj.plugin.api.model.mock.EpisodeMock;
import org.yamj.plugin.api.model.mock.SeasonMock;
import org.yamj.plugin.api.model.mock.SeriesMock;
import org.yamj.plugin.api.service.mock.PluginConfigServiceMock;
import org.yamj.plugin.api.service.mock.PluginLocaleServiceMock;
import org.yamj.plugin.api.service.mock.PluginMetadataServiceMock;
import ro.fortsoft.pf4j.PluginWrapper;

public class TheMovieDbArtworkScannerTest {
    
    private static TheMovieDbPlugin plugin;
    private static TheMovieDbArtworkScanner artworkScanner;
    
    @BeforeClass
    @SuppressWarnings("resource")
    public static void setUpClass() throws Exception {
        PluginConfigServiceMock configService = new PluginConfigServiceMock();
        PluginMetadataServiceMock metadataService = new PluginMetadataServiceMock();
        PluginLocaleServiceMock localeService = new PluginLocaleServiceMock();
        CommonHttpClient httpClient = new HttpClientWrapper(new SimpleHttpClientBuilder().build());
        
        plugin = new TheMovieDbPlugin(new PluginWrapper(null, null, null, null));
        plugin.setConfigService(configService);
        plugin.setHttpClient(httpClient);
        plugin.start();
        
        artworkScanner = new TheMovieDbArtworkScanner();
        artworkScanner.setConfigService(configService);
        artworkScanner.setLocaleService(localeService);
        artworkScanner.setMetadataService(metadataService);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        plugin.stop();
    }

    @SuppressWarnings("rawtypes")
    protected static void logArtworks(List<ArtworkDTO> dtos, Class scannerClass) {
        Logger LOG = LoggerFactory.getLogger(scannerClass);
        for (ArtworkDTO dto : dtos) {
            LOG.info("{}: {}", dto);
        }
    }
    
    @Test
    public void testSeriesPoster() {
        SeriesMock series = new SeriesMock();
        series.addId(SOURCE_TMDB, "1399");
        
        List<ArtworkDTO> dtos = artworkScanner.getPosters(series);
        logArtworks(dtos, artworkScanner.getClass());
    }

    @Test
    public void testSeriesFanart() {
        SeriesMock series = new SeriesMock();
        series.addId(SOURCE_TMDB, "1399");
        
        List<ArtworkDTO> dtos = artworkScanner.getFanarts(series);
        logArtworks(dtos, artworkScanner.getClass());
    }

    @Test
    public void testSeasonPoster() {
        SeriesMock series = new SeriesMock();
        series.addId(SOURCE_TMDB, "1399");
        SeasonMock season = new SeasonMock(1);
        season.setSeries(series);
        
        List<ArtworkDTO> dtos = artworkScanner.getPosters(season);
        logArtworks(dtos, artworkScanner.getClass());
    }

    @Test
    public void testSeasonFanart() {
        SeriesMock series = new SeriesMock();
        series.addId(SOURCE_TMDB, "1399");
        SeasonMock season = new SeasonMock(1);
        season.setSeries(series);
        
        List<ArtworkDTO> dtos = artworkScanner.getFanarts(season);
        logArtworks(dtos, artworkScanner.getClass());
    }

    @Test
    public void testVideoImages() {
        SeriesMock series = new SeriesMock();
        series.addId(SOURCE_TMDB, "1399");
        SeasonMock season = new SeasonMock(4);
        season.setSeries(series);
        EpisodeMock episode = new EpisodeMock(2);
        episode.setSeason(season);
        
        List<ArtworkDTO> dtos = artworkScanner.getVideoImages(episode);
        logArtworks(dtos, artworkScanner.getClass());
    }
}