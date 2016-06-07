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
package org.yamj.plugin.moviemeter;

import java.util.List;
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
import ro.fortsoft.pf4j.PluginWrapper;

public class MovieMeterArtworkScannerTest {

    private static final Logger LOG = LoggerFactory.getLogger(MovieMeterArtworkScannerTest.class);

    private static MovieMeterPlugin plugin;
    private static MovieMeterArtworkScanner artworkScanner;
    
    @BeforeClass
    @SuppressWarnings("resource")
    public static void setUpClass() throws Exception {
        PluginConfigServiceMock configService = new PluginConfigServiceMock();
        CommonHttpClient httpClient = new HttpClientWrapper(new SimpleHttpClientBuilder().build());
        
        plugin = new MovieMeterPlugin(new PluginWrapper(null, null, null, null));
        plugin.setConfigService(configService);
        plugin.setHttpClient(httpClient);
        plugin.start();
        
        artworkScanner = new MovieMeterArtworkScanner();
        artworkScanner.setConfigService(configService);
    }
    
    @Test
    public void testGetPoster() {
        MovieMock movie = new MovieMock();
        movie.addId(MovieMeterPlugin.SCANNER_NAME, "17552");
        
        List<ArtworkDTO> dtos = artworkScanner.getPosters(movie);
        if (dtos != null) {
            for (ArtworkDTO dto : dtos) {
                LOG.info("MovieMeter artwork: {}", dto);
            }
        }
    }
}