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
package org.yamj.plugin.trailersland;

import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.http.HttpClientWrapper;
import org.yamj.api.common.http.SimpleHttpClientBuilder;
import org.yamj.plugin.api.model.mock.MovieMock;
import org.yamj.plugin.api.service.mock.PluginConfigServiceMock;
import org.yamj.plugin.api.trailer.TrailerDTO;

public class TrailersLandScannerTest {

    private static final Logger LOG = LoggerFactory.getLogger(TrailersLandScannerTest.class);

    private static TrailersLandScanner trailersScanner;
    
    @BeforeClass
    @SuppressWarnings("resource")
    public static void setUpClass() {
        trailersScanner = new TrailersLandScanner();
        trailersScanner.setConfigService(new PluginConfigServiceMock());
        trailersScanner.setHttpClient(new HttpClientWrapper(new SimpleHttpClientBuilder().build()));
    }
    
    @Test
    public void testMovieTrailers() {
        MovieMock movie = new MovieMock();
        movie.setTitle("Avatar");
        
        List<TrailerDTO> dtos = trailersScanner.scanForTrailer(movie);
        if (dtos != null) {
            for (TrailerDTO dto : dtos) {
                LOG.info("TrailersLand scanned trailer: {}", dto);
            }
        }
    }
}