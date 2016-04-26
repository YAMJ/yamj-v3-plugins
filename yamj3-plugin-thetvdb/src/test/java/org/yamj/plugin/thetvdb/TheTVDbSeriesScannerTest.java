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
package org.yamj.plugin.thetvdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.yamj.plugin.api.service.Constants.SOURCE_TVDB;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.http.CommonHttpClient;
import org.yamj.api.common.http.HttpClientWrapper;
import org.yamj.api.common.http.SimpleHttpClientBuilder;
import org.yamj.plugin.api.metadata.SeriesScanner;
import org.yamj.plugin.api.model.mock.EpisodeMock;
import org.yamj.plugin.api.model.mock.SeasonMock;
import org.yamj.plugin.api.model.mock.SeriesMock;
import org.yamj.plugin.api.service.mock.PluginConfigServiceMock;
import org.yamj.plugin.api.service.mock.PluginLocaleServiceMock;
import org.yamj.plugin.api.service.mock.PluginMetadataServiceMock;
import ro.fortsoft.pf4j.PluginWrapper;

public class TheTVDbSeriesScannerTest {

    private static final Logger LOG = LoggerFactory.getLogger(TheTVDbSeriesScannerTest.class);

    private static TheTvDbPlugin plugin;
    private static SeriesScanner seriesScanner;
    
    @BeforeClass
    @SuppressWarnings("resource")
    public static void setUpClass() throws Exception {
        PluginConfigServiceMock configService = new PluginConfigServiceMock();
        PluginMetadataServiceMock metadataService = new PluginMetadataServiceMock();
        PluginLocaleServiceMock localeService = new PluginLocaleServiceMock();
        CommonHttpClient httpClient = new HttpClientWrapper(new SimpleHttpClientBuilder().build());
        
        plugin = new TheTvDbPlugin(new PluginWrapper(null, null, null, null));
        plugin.setConfigService(configService);
        plugin.setHttpClient(httpClient);
        plugin.start();
        
        seriesScanner = new TheTvDbSeriesScanner();
        seriesScanner.init(configService, metadataService, localeService, httpClient);
    }
    
    /**
     * Test of getScannerName method, of class TheTVDbScanner.
     */
    @Test
    public void testGetScannerName() {
        LOG.info("getScannerName");
        assertEquals("Changed scanner name", SOURCE_TVDB, seriesScanner.getScannerName());
    }

    /**
     * Test of getSeriesId method, of class TheTVDbScanner.
     */
    @Test
    public void testGetSeriesId() {
        LOG.info("getSeriesId");
        SeriesMock series = new SeriesMock();
        series.setTitle("Chuck");
        series.setStartYear(2007);
        String result = seriesScanner.getSeriesId(series, false);

        assertEquals("Wrong ID returned", "80348", result);
    }

    /**
     * Test of scan method, of class TheTVDbScanner.
     */
    @Ignore
    public void testScan() {
        LOG.info("scan");
        SeriesMock series = new SeriesMock();
        series.addId(SOURCE_TVDB, "70726");

        SeasonMock season = new SeasonMock(1);
        series.addSeason(season);
        season.setSeries(series);
        
        EpisodeMock episode = new EpisodeMock(1);
        episode.setSeason(season);
        season.addEpisode(episode);
        
        boolean result = seriesScanner.scanSeries(series, false);

        LOG.info("***** SERIES {} *****", ToStringBuilder.reflectionToString(series, ToStringStyle.MULTI_LINE_STYLE));
        assertEquals("Wrong ScanResult returned", Boolean.TRUE, result);
        assertEquals("Wrong series ID returned", "70726", series.getId(SOURCE_TVDB));
        assertEquals("Wrong title", "Babylon 5", series.getTitle());
        assertFalse("No Genres found", series.getGenres().isEmpty());
    }
}
