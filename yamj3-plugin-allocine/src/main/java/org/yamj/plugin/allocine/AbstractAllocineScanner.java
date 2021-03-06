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
package org.yamj.plugin.allocine;

import static org.yamj.plugin.allocine.AllocinePlugin.SCANNER_NAME;
import static org.yamj.plugin.api.Constants.SOURCE_IMDB;
import static org.yamj.plugin.api.metadata.MetadataTools.isOriginalTitleScannable;

import java.util.Locale;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.http.CommonHttpClient;
import org.yamj.plugin.api.NeedsConfigService;
import org.yamj.plugin.api.NeedsHttpClient;
import org.yamj.plugin.api.NeedsMetadataService;
import org.yamj.plugin.api.metadata.NfoScanner;
import org.yamj.plugin.api.model.*;
import org.yamj.plugin.api.service.PluginConfigService;
import org.yamj.plugin.api.service.PluginMetadataService;
import org.yamj.plugin.api.web.HTMLTools;
import org.yamj.plugin.api.web.SearchEngineTools;
 
public abstract class AbstractAllocineScanner implements NfoScanner, NeedsConfigService, NeedsMetadataService, NeedsHttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractAllocineScanner.class);
    private static final String HTML = ".html";
    
    protected PluginConfigService configService;
    protected PluginMetadataService metadataService;
    protected AllocineApiWrapper allocineApiWrapper;
    private SearchEngineTools searchEngineTools;
    private Lock searchEngineLock;

    @Override
    public final String getScannerName() {
        return SCANNER_NAME;
    }

    @Override
    public final void setConfigService(PluginConfigService configService) {
        this.configService = configService;
        // also set the API wrapper
        this.allocineApiWrapper = AllocinePlugin.getAllocineApiWrapper();
    }

    @Override
    public final void setMetadataService(PluginMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Override
    public final void setHttpClient(CommonHttpClient httpClient) {
        this.searchEngineTools = new SearchEngineTools(httpClient, Locale.FRANCE);
        this.searchEngineLock = new ReentrantLock(true);
    }

    @Override
    public boolean scanNFO(String nfoContent, IdMap idMap) {
        if (configService.getBooleanProperty("allocine.search.imdb", false)) {
            metadataService.scanNFO(SOURCE_IMDB, nfoContent, idMap);
        }

        // if we already have the ID, skip the scanning of the NFO file
        final boolean ignorePresentId = configService.getBooleanProperty("allocine.nfo.ignore.present.id", false);
        if (!ignorePresentId && isValidAllocineId(idMap.getId(SCANNER_NAME))) {
            return true;
        }

        LOG.trace("Scanning NFO for Allocine ID");
        
        // http://www.allocine.fr/...=XXXXX.html
        int beginIndex = StringUtils.indexOfIgnoreCase(nfoContent, "http://www.allocine.fr/");
        if (beginIndex != -1) {
            int beginIdIndex = nfoContent.indexOf('=', beginIndex);
            if (beginIdIndex != -1) {
                int endIdIndex = nfoContent.indexOf('.', beginIdIndex);
                if (endIdIndex != -1) {
                    int id = NumberUtils.toInt(nfoContent.substring(beginIdIndex + 1, endIdIndex), -1);
                    if (id > 0) {
                        String allocineId = Integer.toString(id);
                        LOG.debug("Allocine ID found in NFO: {}", allocineId);
                        idMap.addId(SCANNER_NAME, allocineId);
                        return true;
                    }
                }
            }
        }
        
        LOG.debug("No Allocine ID found in NFO");
        return false;
    }

    protected static boolean isValidAllocineId(String allocineId) {
        return StringUtils.isNumeric(allocineId);
    }

    protected static boolean isNoValidAllocineId(String allocineId) {
        return !isValidAllocineId(allocineId);
    }

    public String getMovieId(IMovie movie, boolean throwTempError) {
        final String allocineId = movie.getId(SCANNER_NAME);
        if (isValidAllocineId(allocineId)) {
            return allocineId;
        }
        
        int id = allocineApiWrapper.getAllocineMovieId(movie.getTitle(), movie.getYear(), throwTempError);

        if (id < 0 && isOriginalTitleScannable(movie)) {
            // try with original title
            id = allocineApiWrapper.getAllocineMovieId(movie.getOriginalTitle(), movie.getYear(), throwTempError);
        }
        
        if (id < 0) {
            // try search engines
            searchEngineLock.lock();
            try {
                searchEngineTools.setSearchSuffix("/fichefilm_gen_cfilm");
                String url = searchEngineTools.searchURL(movie.getTitle(), movie.getYear(), "www.allocine.fr/film", throwTempError);
                id = NumberUtils.toInt(HTMLTools.extractTag(url, "fichefilm_gen_cfilm=", HTML), -1);
            } finally {
                searchEngineLock.unlock();
            }
        }

        return setAllocineId(movie, id);
    }

    public String getSeriesId(ISeries series, boolean throwTempError) {
        final String allocineId = series.getId(SCANNER_NAME);
        if (isValidAllocineId(allocineId)) {
            return allocineId;
        }
        
        int id = allocineApiWrapper.getAllocineSeriesId(series.getTitle(), series.getStartYear(), throwTempError);

        if (id < 0 && isOriginalTitleScannable(series)) {
            // try with original title
            id = allocineApiWrapper.getAllocineSeriesId(series.getOriginalTitle(), series.getStartYear(), throwTempError);
        }

        if (id < 0) {
            // try search engines
            searchEngineLock.lock();
            try {
                searchEngineTools.setSearchSuffix("/ficheserie_gen_cserie");
                String url = searchEngineTools.searchURL(series.getTitle(), series.getStartYear(), "www.allocine.fr/series", throwTempError);
                id = NumberUtils.toInt(HTMLTools.extractTag(url, "ficheserie_gen_cserie=", HTML), -1);
            } finally {
                searchEngineLock.unlock();
            }
        }

        return setAllocineId(series, id);
    }
    
    public String getPersonId(IPerson person, boolean throwTempError) {
        final String allocineId = person.getId(SCANNER_NAME);
        if (isValidAllocineId(allocineId)) {
            return allocineId;
        }

        int id = allocineApiWrapper.getAllocinePersonId(person.getName(), throwTempError);
            
        if (id < 0) {
            // try search engines
            searchEngineLock.lock();
            try {
                searchEngineTools.setSearchSuffix("/fichepersonne_gen_cpersonne");
                String url = searchEngineTools.searchURL(person.getName(), -1, "www.allocine.fr/personne", throwTempError);
                id = NumberUtils.toInt(HTMLTools.extractTag(url, "fichepersonne_gen_cpersonne=", HTML), -1);
            } finally {
                searchEngineLock.unlock();
            }
        }
        
        return setAllocineId(person, id);
    }
    
    private static String setAllocineId(IdMap idMap, int id) {
        if (id > 0) {
            String allocineId = Integer.toString(id);
            idMap.addId(SCANNER_NAME, allocineId);
            return allocineId;
        }
        return null;
    }
}

