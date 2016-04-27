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
package org.yamj.plugin.imdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.yamj.plugin.api.Constants.SOURCE_IMDB;

import java.util.Locale;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.http.CommonHttpClient;
import org.yamj.api.common.http.HttpClientWrapper;
import org.yamj.api.common.http.SimpleHttpClientBuilder;
import org.yamj.plugin.api.model.mock.*;
import org.yamj.plugin.api.service.mock.PluginConfigServiceMock;
import org.yamj.plugin.api.service.mock.PluginLocaleServiceMock;
import ro.fortsoft.pf4j.PluginWrapper;

public class ImdbScannerTest {

    private static final Logger LOG = LoggerFactory.getLogger(ImdbScannerTest.class);

    private static ImdbPlugin plugin;
    private static ImdbMovieScanner movieScanner;
    private static ImdbSeriesScanner seriesScanner;
    private static ImdbPersonScanner personScanner;

    @BeforeClass
    @SuppressWarnings("resource")
    public static void setUpClass() throws Exception {
        PluginConfigServiceMock configService = new PluginConfigServiceMock();
        PluginLocaleServiceMock localeService = new PluginLocaleServiceMock(Locale.GERMANY);
        CommonHttpClient httpClient = new HttpClientWrapper(new SimpleHttpClientBuilder().build());
        
        plugin = new ImdbPlugin(new PluginWrapper(null, null, null, null));
        plugin.setConfigService(configService);
        plugin.setLocaleService(localeService);
        plugin.setHttpClient(httpClient);
        plugin.start();
        
        movieScanner = new ImdbMovieScanner();
        movieScanner.setConfigService(configService);
        movieScanner.setLocaleService(localeService);

        seriesScanner = new ImdbSeriesScanner();
        seriesScanner.setConfigService(configService);
        seriesScanner.setLocaleService(localeService);

        personScanner = new ImdbPersonScanner();
        personScanner.setConfigService(configService);
        personScanner.setLocaleService(localeService);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        plugin.stop();
    }
    
    @Test
    public void testScanMovie() {
        LOG.info("testScanMovie");
        MovieMock movie = new MovieMock();
        movie.addId(SOURCE_IMDB, "tt0499549");
        boolean result = movieScanner.scanMovie(movie, false);

        assertEquals(Boolean.TRUE, result);
        assertEquals("Avatar - Aufbruch nach Pandora", movie.getTitle());
        assertEquals("Avatar", movie.getOriginalTitle());
        assertEquals(2009, movie.getYear());
        assertNotNull(movie.getOutline());
        assertTrue(movie.getGenres().contains("Abenteuer"));
        assertTrue(movie.getGenres().contains("Action"));
        assertTrue(movie.getGenres().contains("Fantasy"));
        assertTrue(movie.getStudios().contains("Twentieth Century Fox Film Corporation"));
        assertTrue(movie.getStudios().contains("Lightstorm Entertainment"));

        LOG.info("{}", movie);
    }

    @Test
    public void testScanSeries() {
        LOG.info("testScanSeries");
        SeriesMock series = new SeriesMock();
        series.addId(SOURCE_IMDB, "tt0944947");
        
        SeasonMock season = new SeasonMock(1);
        series.addSeason(season);
        season.setSeries(series);

        EpisodeMock ep1 = new EpisodeMock(1);
        ep1.setSeason(season);
        season.addEpisode(ep1);

        EpisodeMock ep2 = new EpisodeMock(2);
        ep2.setSeason(season);
        season.addEpisode(ep2);
        
        boolean result = seriesScanner.scanSeries(series, false);

        assertEquals(Boolean.TRUE, result);
        assertEquals("Game of Thrones - Das Lied von Eis und Feuer", series.getTitle());
        assertEquals("Game of Thrones", series.getOriginalTitle());
        assertEquals(2011, series.getStartYear());
        assertEquals(-1, series.getEndYear());
        
        LOG.info("{}", series);
    }

    @Test
    public void testScanPerson() {
        LOG.info("testScanPerson");
        PersonMock person = new PersonMock();
        person.addId(SOURCE_IMDB, "nm0001352");
        boolean result = personScanner.scanPerson(person, false);

        assertEquals(Boolean.TRUE, result);
        assertEquals("Terence Hill", person.getName());
        assertEquals("Mario Girotti", person.getBirthName());
        assertNotNull(person.getBiography());
        assertEquals("Venice, Veneto, Italy", person.getBirthPlace());

        LOG.info("{}", person);
    }
}