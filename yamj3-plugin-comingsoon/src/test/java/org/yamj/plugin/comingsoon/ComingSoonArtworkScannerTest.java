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
package org.yamj.plugin.comingsoon;

import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.http.HttpClientWrapper;
import org.yamj.api.common.http.SimpleHttpClientBuilder;
import org.yamj.plugin.api.artwork.ArtworkDTO;
import org.yamj.plugin.api.model.mock.MovieMock;
import org.yamj.plugin.api.service.mock.PluginConfigServiceMock;

public class ComingSoonArtworkScannerTest {

    private static final Logger LOG = LoggerFactory.getLogger(ComingSoonArtworkScannerTest.class);

    private static ComingSoonArtworkScanner artworkScanner;
    
    @BeforeClass
    @SuppressWarnings("resource")
    public static void setUpClass() {
        artworkScanner = new ComingSoonArtworkScanner();
        artworkScanner.setConfigService(new PluginConfigServiceMock());
        artworkScanner.setHttpClient(new HttpClientWrapper(new SimpleHttpClientBuilder().build()));
    }

    @Test
    public void testGetPoster() {
        MovieMock movie = new MovieMock();
        movie.addId(AbstractComingSoonScanner.SCANNER_NAME, "846");
        
        List<ArtworkDTO> dtos = artworkScanner.getPosters(movie);
        if (dtos != null) {
            for (ArtworkDTO dto : dtos) {
                LOG.info("ComingSoon artwork: {}", dto);
            }
        }
    }
}