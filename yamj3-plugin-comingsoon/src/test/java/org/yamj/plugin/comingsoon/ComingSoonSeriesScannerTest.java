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

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamj.api.common.http.HttpClientWrapper;
import org.yamj.api.common.http.SimpleHttpClientBuilder;
import org.yamj.plugin.api.common.PluginConfigServiceImpl;
import org.yamj.plugin.api.metadata.SeriesScanner;
import org.yamj.plugin.api.metadata.model.Episode;
import org.yamj.plugin.api.metadata.model.Season;
import org.yamj.plugin.api.metadata.model.Series;

public class ComingSoonSeriesScannerTest {

    private static SeriesScanner seriesScanner;
    
    @BeforeClass
    @SuppressWarnings("resource")
    public static void setUpClass() {
        seriesScanner = new ComingSoonSeriesScanner();
        seriesScanner.init(new PluginConfigServiceImpl(), new HttpClientWrapper(new SimpleHttpClientBuilder().build()), Locale.getDefault());
    }
        
    @Test
    public void testGetSeriesId() {
        Map<String,String> ids = Collections.emptyMap();
        String id = seriesScanner.getSeriesId("Two and a half men", null, 2003, ids, false);
        assertEquals("28", id);
    }

    @Test
    public void testScanSeries() {
        Series series = new Series().addId(seriesScanner.getScannerName(), "28");
        Season season = new Season().setSeasonNumber(1);
        season.addEpisode(new Episode().setEpisodeNumber(1));
        season.addEpisode(new Episode().setEpisodeNumber(2));
        series.addSeason(season);

        seriesScanner.scanSeries(series, false);

        assertEquals("Due Uomini E Mezzo", series.getTitle());
        assertEquals("Two and a Half Men", series.getOriginalTitle());
        assertNotNull(series.getPlot());
    }
}