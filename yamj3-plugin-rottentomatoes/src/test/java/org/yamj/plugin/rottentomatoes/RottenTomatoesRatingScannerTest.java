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
package org.yamj.plugin.rottentomatoes;

import static org.junit.Assert.assertEquals;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamj.api.common.http.CommonHttpClient;
import org.yamj.api.common.http.HttpClientWrapper;
import org.yamj.api.common.http.SimpleHttpClientBuilder;
import org.yamj.plugin.api.model.mock.MovieMock;
import org.yamj.plugin.api.service.mock.PluginConfigServiceMock;
import ro.fortsoft.pf4j.PluginWrapper;

public class RottenTomatoesRatingScannerTest{

    private static RottenTomatoesPlugin plugin;
    private static RottenTomatoesRatingScanner extrasScanner;
    
    @BeforeClass
    @SuppressWarnings("resource")
    public static void setUpClass() throws Exception {
        PluginConfigServiceMock configService = new PluginConfigServiceMock();
        CommonHttpClient httpClient = new HttpClientWrapper(new SimpleHttpClientBuilder().build());
        
        plugin = new RottenTomatoesPlugin(new PluginWrapper(null, null, null, null));
        plugin.setConfigService(configService);
        plugin.setHttpClient(httpClient);
        plugin.start();
        
        extrasScanner = new RottenTomatoesRatingScanner();
        extrasScanner.setConfigService(configService);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        plugin.stop();
    }

    @Test
    public void testScanRating() {
        MovieMock movie = new MovieMock();
        movie.setTitle("Avatar");
        movie.setYear(2009);
        extrasScanner.scanExtras(movie);
        
        assertEquals(83, movie.getRating());
    }
}