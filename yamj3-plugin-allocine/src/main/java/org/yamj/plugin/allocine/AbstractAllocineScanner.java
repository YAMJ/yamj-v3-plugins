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
import static org.yamj.plugin.api.common.Constants.SOURCE_IMDB;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.http.CommonHttpClient;
import org.yamj.plugin.api.common.PluginConfigService;
import org.yamj.plugin.api.common.PluginLocaleService;
import org.yamj.plugin.api.common.PluginMetadataService;
import org.yamj.plugin.api.metadata.*;
import org.yamj.plugin.api.web.HTMLTools;
import org.yamj.plugin.api.web.SearchEngineTools;
 
public abstract class AbstractAllocineScanner implements MetadataScanner, NfoIdScanner {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractAllocineScanner.class);

    protected PluginConfigService configService;
    protected PluginMetadataService metadataService;
    protected AllocineApiWrapper allocineApiWrapper;
    private SearchEngineTools searchEngineTools;
    private Lock searchEngineLock;

    @Override
    public String getScannerName() {
        return SCANNER_NAME;
    }

    @Override
    public void init(PluginConfigService configService, PluginMetadataService metadataService, PluginLocaleService localeService, CommonHttpClient httpClient) {
        this.configService = configService;
        this.metadataService = metadataService;
        this.allocineApiWrapper = AllocinePlugin.getAllocineApiWrapper();
        
        this.searchEngineTools = new SearchEngineTools(httpClient, Locale.FRANCE);
        this.searchEngineLock = new ReentrantLock(true);
    }

    @Override
    public boolean scanNFO(String nfoContent, IdMap idMap) {
        if (configService.getBooleanProperty("allocine.search.imdb", false)) {
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
                        String sourceId = Integer.toString(id);
                        LOG.debug("Allocine ID found in NFO: {}", sourceId);
                        idMap.addId(SCANNER_NAME, sourceId);
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

    public String getMovieId(String title, String originalTitle, int year, Map<String, String> ids, boolean throwTempError) {
        String allocineId = ids.get(SCANNER_NAME);
        if (isValidAllocineId(allocineId)) {
            return allocineId;
        }
        
        int id = allocineApiWrapper.getAllocineMovieId(title, year, throwTempError);

        if (id < 0 && MetadataTools.isOriginalTitleScannable(title, originalTitle)) {
            // try with original title
            id = allocineApiWrapper.getAllocineMovieId(originalTitle, year, throwTempError);
        }
        
        if (id < 0) {
            // try search engines
            searchEngineLock.lock();
            try {
                searchEngineTools.setSearchSuffix("/fichefilm_gen_cfilm");
                String url = searchEngineTools.searchURL(title, year, "www.allocine.fr/film", throwTempError);
                id = NumberUtils.toInt(HTMLTools.extractTag(url, "fichefilm_gen_cfilm=", ".html"), -1);
            } finally {
                searchEngineLock.unlock();
            }
        }
        
        return (id > 0 ? Integer.toString(id) : null);
    }

    public String getSeriesId(String title, String originalTitle, int year, Map<String, String> ids, boolean throwTempError) {
        String allocineId = ids.get(SCANNER_NAME);
        if (isValidAllocineId(allocineId)) {
            return allocineId;
        }
        
        int id = allocineApiWrapper.getAllocineSeriesId(title, year, throwTempError);

        if (id < 0 && MetadataTools.isOriginalTitleScannable(title, originalTitle)) {
            // try with original title
            id = allocineApiWrapper.getAllocineSeriesId(originalTitle, year, throwTempError);
        }

        if (id < 0) {
            // try search engines
            searchEngineLock.lock();
            try {
                searchEngineTools.setSearchSuffix("/ficheserie_gen_cserie");
                String url = searchEngineTools.searchURL(title, year, "www.allocine.fr/series", throwTempError);
                id = NumberUtils.toInt(HTMLTools.extractTag(url, "ficheserie_gen_cserie=", ".html"), -1);
            } finally {
                searchEngineLock.unlock();
            }
        }
        
        return (id > 0 ? Integer.toString(id) : null);
    }
    
    public String getPersonId(String name, Map<String, String> ids, boolean throwTempError) {
        String allocineId = ids.get(SCANNER_NAME);
        if (isValidAllocineId(allocineId)) {
            return allocineId;
        }

        int id = allocineApiWrapper.getAllocinePersonId(name, throwTempError);
            
        if (id < 0) {
            // try search engines
            searchEngineLock.lock();
            try {
                searchEngineTools.setSearchSuffix("/fichepersonne_gen_cpersonne");
                String url = searchEngineTools.searchURL(name, -1, "www.allocine.fr/personne", throwTempError);
                id = NumberUtils.toInt(HTMLTools.extractTag(url, "fichepersonne_gen_cpersonne=", ".html"), -1);
            } finally {
                searchEngineLock.unlock();
            }
        }
        
        return (id > 0 ? Integer.toString(id) : null);
    }
}

