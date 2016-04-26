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

import static org.yamj.plugin.api.Constants.SOURCE_IMDB;
import static org.yamj.plugin.api.Constants.SOURCE_TVDB;

import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.http.CommonHttpClient;
import org.yamj.plugin.api.artwork.ArtworkScanner;
import org.yamj.plugin.api.metadata.*;
import org.yamj.plugin.api.model.ISeries;
import org.yamj.plugin.api.model.IdMap;
import org.yamj.plugin.api.service.PluginConfigService;
import org.yamj.plugin.api.service.PluginLocaleService;
import org.yamj.plugin.api.service.PluginMetadataService;
 
public abstract class AbstractTheTvDbScanner implements ArtworkScanner, MetadataScanner, NfoIdScanner {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractTheTvDbScanner.class);

    protected PluginConfigService configService;
    protected PluginMetadataService metadataService;
    protected TheTvDbApiWrapper theTvDbApiWrapper;
    protected Locale locale;

    @Override
    public final String getScannerName() {
        return SOURCE_TVDB;
    }

    @Override
    public void init(PluginConfigService configService, PluginMetadataService metadataService, PluginLocaleService localeService, CommonHttpClient httpClient) {
        this.configService = configService;
        this.metadataService = metadataService;
        this.theTvDbApiWrapper = TheTvDbPlugin.getTheTvDbApiWrapper();
        this.locale = localeService.getLocale();
    }

    @Override
    public boolean scanNFO(String nfoContent, IdMap idMap) {
        if (configService.getBooleanProperty("thetvdb.search.imdb", false)) {
            try {
                MovieScanner imdbScanner = metadataService.getMovieScanner(SOURCE_IMDB);
                if (imdbScanner != null) {
                    imdbScanner.scanNFO(nfoContent, idMap);
                }
            } catch (Exception ex) {
                LOG.error("Failed to scan for IMDb ID in NFO", ex);
            }
        }

        // if we already have the ID, skip the scanning of the NFO file
        final boolean ignorePresentId = configService.getBooleanProperty("thetvdb.nfo.ignore.present.id", false);
        if (!ignorePresentId && isValidTheTvDbId(idMap.getId(SOURCE_TVDB))) {
            return true;
        }

        LOG.trace("Scanning NFO for TheTvDb ID");

        String compareString = nfoContent.toUpperCase();
        int idx = compareString.indexOf("THETVDB.COM");
        if (idx > -1) {
            int beginIdx = compareString.indexOf("&ID=");
            int length = 4;
            if (beginIdx < idx) {
                beginIdx = compareString.indexOf("?ID=");
            }
            if (beginIdx < idx) {
                beginIdx = compareString.indexOf("&SERIESID=");
                length = 10;
            }
            if (beginIdx < idx) {
                beginIdx = compareString.indexOf("?SERIESID=");
                length = 10;
            }

            if (beginIdx > idx) {
                int endIdx = compareString.indexOf("&", beginIdx + 1);
                String id;
                if (endIdx > -1) {
                    id = compareString.substring(beginIdx + length, endIdx);
                } else {
                    id = compareString.substring(beginIdx + length);
                }

                if (StringUtils.isNotBlank(id)) {
                    String sourceId = id.trim();
                    idMap.addId(SOURCE_TVDB, sourceId);
                    LOG.debug("TheTvDb ID found in NFO: {}", sourceId);
                    return true;
                }
            }
        }
    
        LOG.debug("No TheTvDb ID found in NFO");
        return false;
    }

    public String getSeriesId(ISeries series, boolean throwTempError) {
        String tvdbId = series.getId(SOURCE_TVDB);

        // search by title
        if (isNoValidTheTvDbId(tvdbId)) {
            tvdbId = this.theTvDbApiWrapper.getSeriesId(series.getTitle(), series.getStartYear(), locale.getLanguage(), throwTempError);
        }
        
        // search by original title
        if (isNoValidTheTvDbId(tvdbId) && MetadataTools.isOriginalTitleScannable(series.getTitle(), series.getOriginalTitle())) {
            tvdbId = theTvDbApiWrapper.getSeriesId(series.getOriginalTitle(), series.getStartYear(), locale.getLanguage(), throwTempError);
        }
        
        return tvdbId;
    }

    protected static boolean isValidTheTvDbId(String tvdbId) {
        return StringUtils.isNumeric(tvdbId);
    }

    protected static boolean isNoValidTheTvDbId(String tvdbId) {
        return !isValidTheTvDbId(tvdbId);
    }
}

