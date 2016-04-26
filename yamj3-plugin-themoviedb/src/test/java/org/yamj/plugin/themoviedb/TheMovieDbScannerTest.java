/*
 *      Copyright (c) 2004-2015 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ).
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
 *      Web: https://github.com/YAMJ/yamj-v3
 *
 */
package org.yamj.plugin.themoviedb;

import static org.junit.Assert.assertEquals;
import static org.yamj.plugin.api.service.Constants.SOURCE_TMDB;

import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.http.CommonHttpClient;
import org.yamj.api.common.http.HttpClientWrapper;
import org.yamj.api.common.http.SimpleHttpClientBuilder;
import org.yamj.plugin.api.metadata.*;
import org.yamj.plugin.api.model.mock.*;
import org.yamj.plugin.api.service.mock.PluginConfigServiceMock;
import org.yamj.plugin.api.service.mock.PluginLocaleServiceMock;
import org.yamj.plugin.api.service.mock.PluginMetadataServiceMock;
import ro.fortsoft.pf4j.PluginWrapper;

public class TheMovieDbScannerTest {

    private static final Logger LOG = LoggerFactory.getLogger(TheMovieDbScannerTest.class);
    
    private static TheMovieDbPlugin plugin;
    private static MovieScanner movieScanner;
    private static SeriesScanner seriesScanner;
    private static PersonScanner personScanner;
    private static FilmographyScanner filmographyScanner;
    
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
        
        movieScanner = new TheMovieDbMovieScanner();
        movieScanner.init(configService, metadataService, localeService, httpClient);

        seriesScanner = new TheMovieDbSeriesScanner();
        seriesScanner.init(configService, metadataService, localeService, httpClient);

        personScanner = new TheMovieDbPersonScanner();
        personScanner.init(configService, metadataService, localeService, httpClient);

        filmographyScanner = new TheMovieDbFilmographyScanner();
        filmographyScanner.init(configService, metadataService, localeService, httpClient);
    }

    /**
     * Test of getScannerName method, of class TheTVDbScanner.
     */
    @Test
    public void testGetScannerName() {
        LOG.info("testGetScannerName");
        String result = movieScanner.getScannerName();
        assertEquals("Changed scanner name", SOURCE_TMDB, result);
    }

    @Test
    public void testScanMovie() {
        LOG.info("testScanMovie");
        MovieMock mock = new MovieMock();
        mock.addId(SOURCE_TMDB, "19995");
        boolean result = movieScanner.scanMovie(mock, false);

        assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void testScanFilmography() {
        LOG.info("testScanFilmography");
        List<FilmographyDTO> result = filmographyScanner.scanFilmography("12795", false);

        // Test that we get an error when scanning without an ID
        assertEquals(Boolean.FALSE, result.isEmpty());
        for (FilmographyDTO dto : result) {
            LOG.info("Filmo: {}", dto);
        }
    }

    @Test
    public void testGetSeriesId() {
        LOG.info("testGetSeriesId");
        SeriesMock mock = new SeriesMock();
        mock.setTitle("Game Of Thrones - Das Lied von Eis und Feuer");
        
        String id = seriesScanner.getSeriesId(mock, false);
        assertEquals("1399", id);
    }

    @Test
    public void testScanSeries() {
        LOG.info("testScanSeries");
        SeriesMock seriesMock = new SeriesMock();
        seriesMock.addId(SOURCE_TMDB, "1399");
        
        SeasonMock seasonMock = new SeasonMock(1);
        seriesMock.addSeason(seasonMock);
        seasonMock.setSeries(seriesMock);
        
        EpisodeMock episodeMock = new EpisodeMock(5);
        episodeMock.setSeason(seasonMock);
        seasonMock.addEpisode(episodeMock);
        
        boolean result = seriesScanner.scanSeries(seriesMock, false);

        assertEquals(Boolean.TRUE, result);
        LOG.info("Credits: {}", episodeMock.getCredits());
    }
}