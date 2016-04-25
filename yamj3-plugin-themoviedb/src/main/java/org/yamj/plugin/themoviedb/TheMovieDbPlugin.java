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
package org.yamj.plugin.themoviedb;

import static org.yamj.plugin.api.service.Constants.SOURCE_TMDB;

import com.omertron.themoviedbapi.TheMovieDbApi;
import java.io.InputStream;
import java.util.Properties;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.PersistenceConfiguration;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.YamjPlugin;
import ro.fortsoft.pf4j.PluginException;
import ro.fortsoft.pf4j.PluginWrapper;

public class TheMovieDbPlugin extends YamjPlugin {
    
    private static final Logger LOG = LoggerFactory.getLogger(TheMovieDbPlugin.class);
    private static TheMovieDbApiWrapper theMovieDbApiWrapper;
    
    public TheMovieDbPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() throws PluginException {
        LOG.trace("Start TheMovieDbPlugin");
        
        // create API
        try (InputStream stream = getClass().getResourceAsStream("/themoviedb.apikey.properties")) {
            Properties props = new Properties();
            props.load(stream);

            // create API
            final String apiKey = props.getProperty("apikey.themoviedb");
            TheMovieDbApi tmdbApi = new TheMovieDbApi(apiKey, httpClient);
            
            // create cache
            Cache cache = new Cache(new CacheConfiguration().name(SOURCE_TMDB)
                            .eternal(false)
                            .maxEntriesLocalHeap(200)
                            .timeToIdleSeconds(0)
                            .timeToLiveSeconds(1800)
                            .persistence(new PersistenceConfiguration().strategy(PersistenceConfiguration.Strategy.NONE))
                            .memoryStoreEvictionPolicy(MemoryStoreEvictionPolicy.LRU)
                            .statistics(false));
            
            // normally the YAMJ cache manager will be used
            CacheManager.getInstance().addCache(cache);
            
            theMovieDbApiWrapper = new TheMovieDbApiWrapper(tmdbApi, configService, cache);
        } catch (Exception ex) {
            throw new PluginException("Failed to create TheMovieDb api", ex);
        }
        
        // load properties
        try (InputStream stream = getClass().getResourceAsStream("/themoviedb.plugin.properties")) {
            Properties props = new Properties();
            props.load(stream);
            configService.pluginConfiguration(props);
        } catch (Exception ex) {
            throw new PluginException("Failed to load plugin properties", ex);
        }
    }

    @Override
    public void stop() throws PluginException {
        LOG.trace("Stop TheMovieDbPlugin");
    }

    public static TheMovieDbApiWrapper getTheMovieDbApiWrapper() {
        return theMovieDbApiWrapper;
    }
}