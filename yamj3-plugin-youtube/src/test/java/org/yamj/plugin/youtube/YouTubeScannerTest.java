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
package org.yamj.plugin.youtube;

import java.util.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.model.mock.MovieMock;
import org.yamj.plugin.api.service.mock.PluginConfigServiceMock;
import org.yamj.plugin.api.service.mock.PluginLocaleServiceMock;
import org.yamj.plugin.api.trailer.TrailerDTO;
import ro.fortsoft.pf4j.PluginWrapper;

public class YouTubeScannerTest {

    private static final Logger LOG = LoggerFactory.getLogger(YouTubeScannerTest.class);
    
    private static YouTubePlugin plugin;
    private static YouTubeScanner trailerScanner;
    
    @BeforeClass
    public static void setUpClass() throws Exception {
        PluginConfigServiceMock configService = new PluginConfigServiceMock();
        PluginLocaleServiceMock localeService = new PluginLocaleServiceMock();
        
        plugin = new YouTubePlugin(new PluginWrapper(null, null, null, null));
        plugin.setConfigService(configService);
        plugin.start();
        
        trailerScanner = new YouTubeScanner();
        trailerScanner.setConfigService(configService);
        trailerScanner.setLocaleService(localeService);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        plugin.stop();
    }

    @Test
    public void testMovieTrailers() {
        MovieMock movie = new MovieMock();
        movie.setTitle("Avatar");
        
        List<TrailerDTO> dtos = trailerScanner.scanForTrailer(movie);
        if (dtos != null) {
            for (TrailerDTO dto : dtos) {
                LOG.info("YouTube scanned trailer: {}", dto);
            }
        }
    }
}