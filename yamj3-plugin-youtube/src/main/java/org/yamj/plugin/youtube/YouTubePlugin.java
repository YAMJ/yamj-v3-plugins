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
package org.yamj.plugin.youtube;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.NeedsConfigService;
import org.yamj.plugin.api.service.PluginConfigService;
import ro.fortsoft.pf4j.Plugin;
import ro.fortsoft.pf4j.PluginException;
import ro.fortsoft.pf4j.PluginWrapper;

public class YouTubePlugin extends Plugin implements NeedsConfigService {
    
    private static final Logger LOG = LoggerFactory.getLogger(YouTubePlugin.class);
    public static final String SCANNER_NAME = "youtube";
    public static final String TRAILER_BASE_URL = "https://www.youtube.com/watch?v=";
    public static final String TRAILER_INFO_URL = "https://www.youtube.com/get_video_info?authuser=0&el=embedded&video_id=";

    private PluginConfigService configService;
    private static String youTubeApiKey;
    private static YouTube youTube;
    
    public YouTubePlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void setConfigService(PluginConfigService configService) {
        this.configService = configService;
    }

    @Override
    public void start() throws PluginException {
        LOG.trace("Start YouTubePlugin");

        // create API
        try (InputStream stream = getClass().getResourceAsStream("/youtube.apikey.properties")) {
            Properties props = new Properties();
            props.load(stream);

            // create API
            youTubeApiKey = props.getProperty("apikey.youtube");
            if (StringUtils.isBlank(youTubeApiKey)) {
                throw new PluginException("No YouTube api key provided");
            }
            
            // this object is used to make YouTube Data API requests
            youTube = new YouTube.Builder(new NetHttpTransport(), new JacksonFactory(),
                new HttpRequestInitializer() {
                    @Override
                    public void initialize(HttpRequest request) throws IOException {
                        // nothing to do
                    }
                })
                .setApplicationName("youtube-yamj3-search")
                .build();

        } catch (PluginException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PluginException("Failed to create YouTube api", ex);
        }

        // load properties
        try (InputStream stream = getClass().getResourceAsStream("/youtube.plugin.properties")) {
            Properties props = new Properties();
            props.load(stream);
            configService.pluginConfiguration(props);
        } catch (Exception ex) {
            throw new PluginException("Failed to load youtube properties", ex);
        }
    }

    @Override
    public void stop() throws PluginException {
        LOG.trace("Stop YouTubePlugin");
    }

    public static String getYouTubeApiKey() {
        return youTubeApiKey;
    }
    
    public static YouTube getYouTube() {
        return youTube;
    }
}