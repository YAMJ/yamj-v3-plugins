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
package org.yamj.plugin.fanarttv;

import com.omertron.fanarttvapi.FanartTvApi;
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
import org.yamj.plugin.api.NeedsHttpClient;
import ro.fortsoft.pf4j.Plugin;
import ro.fortsoft.pf4j.PluginException;
import ro.fortsoft.pf4j.PluginWrapper;

public class FanartTvPlugin extends Plugin implements NeedsHttpClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(FanartTvPlugin.class);
    private static FanartTvApiWrapper fanartTvApiWrapper;
    private CommonHttpClient httpClient;
    
    public FanartTvPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void setHttpClient(CommonHttpClient httpClient) {
        this.httpClient = httpClient;
    }   

    @Override
    public void start() throws PluginException {
        LOG.trace("Start FanartTvPlugin");
        
        // create API
        try (InputStream stream = getClass().getResourceAsStream("/fanartttv.apikey.properties")) {
            Properties props = new Properties();
            props.load(stream);

            // create API
            final String apiKey = props.getProperty("apikey.fanarttv.apiKey");
            final String clientKey = props.getProperty("apikey.fanarttv.clientKey");
            FanartTvApi fanartTvApi = new FanartTvApi(apiKey, clientKey, httpClient);
            
            // create cache
            Cache cache = new Cache(new CacheConfiguration().name("fanarttv")
                            .eternal(false)
                            .maxEntriesLocalHeap(200)
                            .timeToIdleSeconds(0)
                            .timeToLiveSeconds(1800)
                            .persistence(new PersistenceConfiguration().strategy(PersistenceConfiguration.Strategy.NONE))
                            .memoryStoreEvictionPolicy(MemoryStoreEvictionPolicy.LRU)
                            .statistics(false));
            
            // normally the YAMJ cache manager will be used
            CacheManager.getInstance().addCache(cache);
            
            fanartTvApiWrapper = new FanartTvApiWrapper(fanartTvApi, cache);
        } catch (Exception ex) {
            throw new PluginException("Failed to create FanartTV api", ex);
        }
    }

    @Override
    public void stop() throws PluginException {
        LOG.trace("Stop FanartTvPlugin");
    }

    public static FanartTvApiWrapper getFanartTvApiWrapper() {
        return fanartTvApiWrapper;
    }
}