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

import static org.yamj.plugin.youtube.YouTubePlugin.SCANNER_NAME;
import static org.yamj.plugin.youtube.YouTubePlugin.TRAILER_BASE_URL;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ResourceId;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.NeedsConfigService;
import org.yamj.plugin.api.NeedsLocaleService;
import org.yamj.plugin.api.model.IMovie;
import org.yamj.plugin.api.model.type.ContainerType;
import org.yamj.plugin.api.service.PluginConfigService;
import org.yamj.plugin.api.service.PluginLocaleService;
import org.yamj.plugin.api.trailer.MovieTrailerScanner;
import org.yamj.plugin.api.trailer.TrailerDTO;
import ro.fortsoft.pf4j.Extension;

@Extension
public class YouTubeScanner implements MovieTrailerScanner, NeedsConfigService, NeedsLocaleService {

    private static final Logger LOG = LoggerFactory.getLogger(YouTubeScanner.class);
    private static final String YOUTUBE_VIDEO = "youtube#video";
    
    private PluginConfigService configService;
    private PluginLocaleService localeService;
    
    @Override
    public String getScannerName() {
        return SCANNER_NAME;
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
    public List<TrailerDTO> scanForTrailer(IMovie movie) {
        final Locale locale = localeService.getLocale();

        try {
            StringBuilder query = new StringBuilder();
            query.append(movie.getTitle());
            if (movie.getYear() > 0) {
                query.append(" ").append(movie.getYear());
            }
            query.append(" trailer");

            String additionalSearch = configService.getProperty("youtube.trailer.additionalSearch");
            if (StringUtils.isNotBlank(additionalSearch)) {
                query.append(" ").append(additionalSearch);
            } else {
                query.append(" ").append(localeService.getDisplayLanguage(locale.getLanguage(), locale.getLanguage()));
            }

            // define the API request for retrieving search results
            YouTube.Search.List search = YouTubePlugin.getYouTube().search().list("id,snippet");
            search.setKey(YouTubePlugin.getYouTubeApiKey());
            search.setQ(query.toString());
            search.setType("video");
            search.setMaxResults(Long.valueOf(configService.getLongProperty("yamj3.trailer.scanner.movie.maxResults", 5)));
            
            if (configService.getBooleanProperty("youtube.trailer.hdwanted", true)) {
                search.setVideoDefinition("high");
            }
            
            final String regionCode = configService.getProperty("youtube.trailer.regionCode");
            if (StringUtils.isNotBlank(regionCode)) {
                search.setRegionCode(regionCode);
            } else {
                search.setRegionCode(locale.getCountry());
            }

            final String relevanceLanguage = configService.getProperty("youtube.trailer.relevanceLanguage");
            if (StringUtils.isNotBlank(relevanceLanguage)) {
                search.setRelevanceLanguage(relevanceLanguage);
            } else {
                search.setRelevanceLanguage(locale.getLanguage());
            }
            
            SearchListResponse searchResponse = search.execute();
            if (CollectionUtils.isEmpty(searchResponse.getItems())) {
                LOG.trace("Found no trailers for movie '{}'", movie.getTitle());
            } else {
                LOG.trace("Found {} trailers for movie '{}'", searchResponse.getItems().size(), movie.getTitle());
                
                List<TrailerDTO> trailers = new ArrayList<>(searchResponse.getItems().size());
                for (SearchResult item : searchResponse.getItems()) {
                    ResourceId resourceId = item.getId();
                    if (YOUTUBE_VIDEO.equals(resourceId.getKind())) {
                        trailers.add(new TrailerDTO(SCANNER_NAME, ContainerType.MP4,
                                        TRAILER_BASE_URL + resourceId.getVideoId(),
                                        item.getSnippet().getTitle(),
                                        resourceId.getVideoId()));
                    }
                }
                return trailers;
            }
        } catch (Exception e) {
            LOG.error("YouTube trailer scanner error: '" + movie.getTitle() + "'", e);
        }
        
        return null;
    }
}