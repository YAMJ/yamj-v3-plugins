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
package org.yamj.plugin.rottentomatoes;

import com.omertron.rottentomatoesapi.RottenTomatoesApi;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.http.CommonHttpClient;
import org.yamj.plugin.api.NeedsConfigService;
import org.yamj.plugin.api.NeedsHttpClient;
import org.yamj.plugin.api.service.PluginConfigService;
import ro.fortsoft.pf4j.Plugin;
import ro.fortsoft.pf4j.PluginException;
import ro.fortsoft.pf4j.PluginWrapper;

public class RottenTomatoesPlugin extends Plugin implements NeedsConfigService, NeedsHttpClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(RottenTomatoesPlugin.class);
    private static RottenTomatoesApi rottenTomatoesApi;
    private PluginConfigService configService;
    private CommonHttpClient httpClient;

    public RottenTomatoesPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void setConfigService(PluginConfigService configService) {
        this.configService = configService;
    }

    @Override
    public void setHttpClient(CommonHttpClient httpClient) {
        this.httpClient = httpClient;
    }    

    @Override
    public void start() throws PluginException {
        LOG.trace("Start RottenTomatoesPlugin");

        // create API
        try (InputStream stream = getClass().getResourceAsStream("/rottentomatoes.apikey.properties")) {
            Properties props = new Properties();
            props.load(stream);

            final String apiKey = props.getProperty("apikey.rottentomatoes");
            rottenTomatoesApi = new RottenTomatoesApi(apiKey, httpClient);
        } catch (Exception ex) {
            throw new PluginException("Failed to create rottentomatoes api", ex);
        }
        
        // load properties
        try (InputStream stream = getClass().getResourceAsStream("/rottentomatoes.plugin.properties")) {
            Properties props = new Properties();
            props.load(stream);
            configService.pluginConfiguration(props);
        } catch (Exception ex) {
            throw new PluginException("Failed to load rottentomatoes properties", ex);
        }
    }

    @Override
    public void stop() throws PluginException {
        LOG.trace("Stop RottenTomatoesPlugin");
    }
    
    public static RottenTomatoesApi getRottenTomatoesApi() {
        return rottenTomatoesApi;
    }
}