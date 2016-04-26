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
import com.omertron.fanarttvapi.FanartTvException;
import com.omertron.fanarttvapi.model.FTMovie;
import com.omertron.fanarttvapi.model.FTSeries;
import net.sf.ehcache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.tools.EhCacheWrapper;

public class FanartTvApiWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(FanartTvApiWrapper.class);
    private static final String API_ERROR = "FanartTV scanner error";

    private final FanartTvApi fanartTvApi;
    private final EhCacheWrapper cache;
    
    public FanartTvApiWrapper(FanartTvApi fanartTvApi, Cache cache) {
        this.fanartTvApi = fanartTvApi;
        this.cache = new EhCacheWrapper(cache);
    }
    
    public FTMovie getFanartMovie(String id) { 
        FTMovie ftMovie = null;
        try {
            final String cacheKey = "movie###"+id;
            ftMovie = cache.get(cacheKey, FTMovie.class);
            if (ftMovie == null) {
                ftMovie = fanartTvApi.getMovieArtwork(id);
                cache.store(cacheKey, ftMovie);
            }
        } catch (FanartTvException ex) {
            LOG.error("Failed to get movie artwork from FanartTV for id {}: {}", id, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        return ftMovie;
    }

    public FTSeries getFanartSeries(String id) { 
        FTSeries ftSeries = null;
        try {
            final String cacheKey = "series###"+id;
            ftSeries = cache.get(cacheKey, FTSeries.class);
            if (ftSeries == null) {
                ftSeries = fanartTvApi.getTvArtwork(id);
                cache.store(cacheKey, ftSeries);
            }
        } catch (FanartTvException ex) {
            LOG.error("Failed to get series artwork from FanartTV for id {}: {}", id, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        return ftSeries;
    }
}
