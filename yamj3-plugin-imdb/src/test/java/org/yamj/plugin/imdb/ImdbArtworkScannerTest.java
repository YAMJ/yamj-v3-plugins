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

import static org.yamj.plugin.api.Constants.SOURCE_IMDB;

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
import org.yamj.plugin.api.model.mock.MovieMock;
import org.yamj.plugin.api.service.mock.PluginConfigServiceMock;
import org.yamj.plugin.api.service.mock.PluginLocaleServiceMock;
import ro.fortsoft.pf4j.PluginWrapper;

public class ImdbArtworkScannerTest {

    private static ImdbPlugin plugin;
    private static ImdbArtworkScanner artworkScanner;
    
    @BeforeClass
    @SuppressWarnings("resource")
    public static void setUpClass() throws Exception {
        PluginConfigServiceMock configService = new PluginConfigServiceMock();
        PluginLocaleServiceMock localeService = new PluginLocaleServiceMock();
        CommonHttpClient httpClient = new HttpClientWrapper(new SimpleHttpClientBuilder().build());
        
        plugin = new ImdbPlugin(new PluginWrapper(null, null, null, null));
        plugin.setConfigService(configService);
        plugin.setLocaleService(localeService);
        plugin.setHttpClient(httpClient);
        plugin.start();
        
        artworkScanner = new ImdbArtworkScanner();
        artworkScanner.setConfigService(configService);
        artworkScanner.setLocaleService(localeService);
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
    public void testMoviePosters() {
        MovieMock movie = new MovieMock();
        movie.addId(SOURCE_IMDB, "tt0499549");

        List<ArtworkDTO> dtos = artworkScanner.getPosters(movie);
        logArtworks(dtos, getClass());
    }

    @Test
    public void testMovieFanarts() {
        MovieMock movie = new MovieMock();
        movie.addId(SOURCE_IMDB, "tt0499549");

        List<ArtworkDTO> dtos = artworkScanner.getFanarts(movie);
        logArtworks(dtos, getClass());
    }
}