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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamj.api.common.http.HttpClientWrapper;
import org.yamj.api.common.http.SimpleHttpClientBuilder;
import org.yamj.plugin.api.metadata.SeriesScanner;
import org.yamj.plugin.api.metadata.mock.EpisodeMock;
import org.yamj.plugin.api.metadata.mock.SeasonMock;
import org.yamj.plugin.api.metadata.mock.SeriesMock;
import org.yamj.plugin.api.service.mock.PluginConfigServiceMock;
import org.yamj.plugin.api.service.mock.PluginLocaleServiceMock;
import org.yamj.plugin.api.service.mock.PluginMetadataServiceMock;

public class ComingSoonSeriesScannerTest {

    private static SeriesScanner seriesScanner;
    
    @BeforeClass
    @SuppressWarnings("resource")
    public static void setUpClass() {
        seriesScanner = new ComingSoonSeriesScanner();
        seriesScanner.init(new PluginConfigServiceMock(), new PluginMetadataServiceMock(), new PluginLocaleServiceMock(), new HttpClientWrapper(new SimpleHttpClientBuilder().build()));
    }
        
    @Test
    public void testGetSeriesId() {
        SeriesMock series = new SeriesMock();
        series.setTitle("Two and a half men");
        series.setStartYear(2003);
        
        String id = seriesScanner.getSeriesId(series, false);
        assertEquals("28", id);
    }

    @Test
    public void testScanSeries() {
        SeriesMock series = new SeriesMock();
        series.addId(seriesScanner.getScannerName(), "28");
        
        SeasonMock season = new SeasonMock(1);
        season.setSeries(series);
        series.addSeason(season);
        
        EpisodeMock ep1 = new EpisodeMock(1);
        ep1.setSeason(season);
        season.addEpisode(ep1);
        EpisodeMock ep2 = new EpisodeMock(2);
        ep2.setSeason(season);
        season.addEpisode(ep2);

        seriesScanner.scanSeries(series, false);

        assertEquals("Due Uomini E Mezzo", series.getTitle());
        assertEquals("Two and a Half Men", series.getOriginalTitle());
        assertNotNull(series.getPlot());
    }
}