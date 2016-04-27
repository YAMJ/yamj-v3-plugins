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
package org.yamj.plugin.imdb;

import static org.yamj.plugin.api.Constants.SOURCE_IMDB;

import com.omertron.imdbapi.ImdbApi;
import java.io.InputStream;
import java.util.Properties;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.PersistenceConfiguration;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.http.CommonHttpClient;
import org.yamj.plugin.api.NeedsConfigService;
import org.yamj.plugin.api.NeedsHttpClient;
import org.yamj.plugin.api.NeedsLocaleService;
import org.yamj.plugin.api.service.PluginConfigService;
import org.yamj.plugin.api.service.PluginLocaleService;
import ro.fortsoft.pf4j.Plugin;
import ro.fortsoft.pf4j.PluginException;
import ro.fortsoft.pf4j.PluginWrapper;

public class ImdbPlugin extends Plugin implements NeedsConfigService, NeedsLocaleService, NeedsHttpClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(ImdbPlugin.class);
    private static ImdbApiWrapper imdbApiWrapper;
    private static ImdbSearchEngine imdbSearchEngine;
    private PluginConfigService configService;
    private PluginLocaleService localeService;
    private CommonHttpClient httpClient;
    private CacheManager cacheManager;
    
    public ImdbPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void setConfigService(PluginConfigService configService) {
        this.configService = configService;
    }

    @Override
    public void setLocaleService(PluginLocaleService localeService) {
        this.localeService = localeService;
    }

    @Override
    public void setHttpClient(CommonHttpClient httpClient) {
        this.httpClient = httpClient;
    }   

    @Override
    public void start() throws PluginException {
        LOG.trace("Start TheMovieDbPlugin");
        
        try {
            // create API
            ImdbApi imdbApi = new ImdbApi(httpClient);
            imdbSearchEngine = new ImdbSearchEngine(configService, localeService, httpClient);
            
            // create cache
            cacheManager = CacheManager.getInstance();
            Cache cache = new Cache(new CacheConfiguration().name(SOURCE_IMDB)
                            .eternal(false)
                            .maxEntriesLocalHeap(200)
                            .timeToIdleSeconds(0)
                            .timeToLiveSeconds(1800)
                            .persistence(new PersistenceConfiguration().strategy(PersistenceConfiguration.Strategy.NONE))
                            .memoryStoreEvictionPolicy(MemoryStoreEvictionPolicy.LRU)
                            .statistics(false));
            
            // normally the YAMJ cache manager will be used
            cacheManager.addCache(cache);
            
            imdbApiWrapper = new ImdbApiWrapper(imdbApi, configService, localeService, httpClient, cache);
        } catch (Exception ex) {
            throw new PluginException("Failed to create IMDb api", ex);
        }
        
        // load properties
        try (InputStream stream = getClass().getResourceAsStream("/imdb.plugin.properties")) {
            Properties props = new Properties();
            props.load(stream);
            configService.pluginConfiguration(props);
        } catch (Exception ex) {
            throw new PluginException("Failed to load imdb properties", ex);
        }
    }

    @Override
    public void stop() throws PluginException {
        LOG.trace("Stop TheMovieDbPlugin");
        
        cacheManager.removeCache(SOURCE_IMDB);
    }
    
    public static ImdbApiWrapper getImdbApiWrapper() {
        return imdbApiWrapper;
    }
    
    public static ImdbSearchEngine getImdbSearchEngine() {
        return imdbSearchEngine;
    }
}