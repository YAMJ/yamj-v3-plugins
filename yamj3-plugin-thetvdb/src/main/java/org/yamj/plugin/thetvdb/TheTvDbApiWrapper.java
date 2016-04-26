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

import com.omertron.thetvdbapi.TheTVDBApi;
import com.omertron.thetvdbapi.TvDbException;
import com.omertron.thetvdbapi.model.*;
import java.util.List;
import net.sf.ehcache.Cache;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.tools.ResponseTools;
import org.yamj.plugin.api.service.PluginConfigService;
import org.yamj.plugin.api.tools.EhCacheWrapper;
import org.yamj.plugin.api.web.TemporaryUnavailableException;

public class TheTvDbApiWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(TheTvDbApiWrapper.class);
    private static final int YEAR_MIN = 1900;
    private static final int YEAR_MAX = 2100;
    private static final String API_ERROR = "TheTVDb error";

    private final TheTVDBApi tvdbApi;
    private final PluginConfigService configService;
    private final EhCacheWrapper cache;

    public TheTvDbApiWrapper(TheTVDBApi tvdbApi, PluginConfigService configService, Cache cache) {
        this.tvdbApi = tvdbApi;
        this.configService = configService;
        this.cache = new EhCacheWrapper(cache);
    }
    
    public Banners getBanners(String id) {
        Banners banners = null;
        
        try {
            final String cacheKey = "banners###"+id;
            banners = cache.get(cacheKey, Banners.class);
            if (banners == null) {
                // retrieve banners from TheTVDb
                banners = tvdbApi.getBanners(id);
                cache.store(cacheKey, banners);
            }
        } catch (Exception ex) {
            LOG.error("Failed to get banners using TVDb ID {}: {}", id, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        
        return banners;
    }

    /**
     * Get series information using the ID
     *
     * @param id
     * @return
     */
    public Series getSeries(String id, String language) {
        return getSeries(id, language, false);
    }
    
    /**
     * Get series information using the ID
     *
     * @param throwTempError
     * @return
     */
    public Series getSeries(String id, String language, boolean throwTempError) {
        Series series = null;

        try {
            String altLanguage = configService.getProperty("thetvdb.language.alternate", language);

            String cacheKey = "series###"+id+"###"+language;
            series = cache.get(cacheKey, Series.class);
            if (series == null) {
                // retrieve series from TheTVDb
                series = tvdbApi.getSeries(id, language);
                cache.store(cacheKey, series);
                if (series == null && !altLanguage.equalsIgnoreCase(language)) {
                    cacheKey = "series###"+id+"###"+altLanguage;                    
                    series = cache.get(cacheKey, Series.class);
                    if (series == null) {
                        series = tvdbApi.getSeries(id, altLanguage);
                        cache.store(cacheKey, series);
                    }
                }
            }
        } catch (TvDbException ex) {
            checkTempError(throwTempError, ex);
            LOG.error("Failed to get series using TVDb ID {}: {}", id, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        
        return series;
    }

    /**
     * Get the Series ID by title and year
     *
     * @param title
     * @param year
     * @return
     */
    public String getSeriesId(String title, int year, String language, boolean throwTempError) {
        String tvdbId = null;

        try {
            String altLanguage = configService.getProperty("thetvdb.language.alternate", language);

            final String cacheKey = "id###"+title+"###"+year;
            tvdbId = cache.get(cacheKey, String.class);
            if (StringUtils.isBlank(tvdbId)) {
                List<Series> seriesList = tvdbApi.searchSeries(title, language);
                if (CollectionUtils.isEmpty(seriesList) && !altLanguage.equalsIgnoreCase(language)) {
                    seriesList = tvdbApi.searchSeries(title, altLanguage);
                }

                if (CollectionUtils.isEmpty(seriesList)) {
                    return StringUtils.EMPTY;
                }
            
                tvdbId = getMatchingSeries(seriesList, year).getId();
                cache.store(cacheKey, tvdbId);
            }
        } catch (TvDbException ex) {
            checkTempError(throwTempError, ex);
            LOG.error("Failed retrieving TVDb id for series '{}': {}", title, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        
        return (tvdbId == null) ? StringUtils.EMPTY : tvdbId;
    }

    private static Series getMatchingSeries(List<Series> seriesList, int year) {
        for (Series s : seriesList) {
            if (s.getFirstAired() != null && !s.getFirstAired().isEmpty() && (year > YEAR_MIN && year < YEAR_MAX)) {
                DateTime firstAired = DateTime.parse(s.getFirstAired());
                firstAired.getYear();
                if (firstAired.getYear() == year) {
                    return s;
                }
            } else {
                return s;
            }
        }
        return new Series();
    }
    
    public List<Actor> getActors(String id) {
        try {
            return tvdbApi.getActors(id);
        } catch (Exception ex) {
            LOG.error("Failed to get actors using TVDb ID {}: {}", id, ex.getMessage());
            LOG.trace(API_ERROR, ex);
            return null;
        }
    }

    public String getSeasonYear(String id, int season, String language) {
        String year = null;
        try {
            String altLanguage = configService.getProperty("thetvdb.language.alternate", language);

            year = tvdbApi.getSeasonYear(id, season, language);
            if (StringUtils.isBlank(year) && !altLanguage.equalsIgnoreCase(language)) {
                year = tvdbApi.getSeasonYear(id, season, altLanguage);
            }
        } catch (Exception ex) {
            LOG.error("Failed to get season year for TVDb ID {} and season {}: {}", id, season, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        
        return year;
    }
        
    public Episode getEpisode(String id, int season, int episode, String language) {
        Episode tvdbEpisode = null;
        
        try {
            String altLanguage = configService.getProperty("thetvdb.language.alternate", language);

            String cacheKey = "episode###"+id+"###"+season+"###"+episode+"###"+language;
            tvdbEpisode = cache.get(cacheKey, Episode.class);
            if (tvdbEpisode == null) {
                // retrieve episode from TheTVDb
                tvdbEpisode = tvdbApi.getEpisode(id, season, episode, language);
                cache.store(cacheKey, tvdbEpisode);
                if (tvdbEpisode == null && !altLanguage.equalsIgnoreCase(language)) {
                    cacheKey = "episode###"+id+"###"+season+"###"+episode+"###"+altLanguage;
                    tvdbEpisode = cache.get(cacheKey, Episode.class);
                    if (tvdbEpisode == null) {
                        tvdbEpisode = tvdbApi.getEpisode(id, season, episode, altLanguage);
                        cache.store(cacheKey, tvdbEpisode);
                    }
                }
            }
        } catch (Exception ex) {
            LOG.error("Failed to get episode {} for TVDb ID {} and season {}: {}", episode, id, season, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        
        return tvdbEpisode;
    }

    private static void checkTempError(boolean throwTempError, TvDbException ex) {
        if (throwTempError && ResponseTools.isTemporaryError(ex)) {
            throw new TemporaryUnavailableException("TheTVDb service temporary not available: " + ex.getResponseCode(), ex);
        }
    }
}
