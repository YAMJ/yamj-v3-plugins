/*
 *      Copyright (c) 2004-2015 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ).
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
 *      Web: https://github.com/YAMJ/yamj-v3
 *
 */
package org.yamj.plugin.allocine;

import com.moviejukebox.allocine.AllocineApi;
import com.moviejukebox.allocine.AllocineException;
import com.moviejukebox.allocine.model.*;
import net.sf.ehcache.Cache;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.tools.ResponseTools;
import org.yamj.plugin.api.tools.EhCacheWrapper;
import org.yamj.plugin.api.web.TemporaryUnavailableException;

public class AllocineApiWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(AllocineApiWrapper.class);
    private static final String API_ERROR = "Allocine error";

    private final AllocineApi allocineApi;
    private final EhCacheWrapper cache;
    
    public AllocineApiWrapper(AllocineApi allocineApi, Cache cache) {
        this.allocineApi = allocineApi;
        this.cache = new EhCacheWrapper(cache);
    }
    
    protected static void checkTempError(boolean throwTempError, AllocineException ex) {
        if (throwTempError && ResponseTools.isTemporaryError(ex)) {
            throw new TemporaryUnavailableException("Allocine service temporary not available: " + ex.getResponseCode(), ex);
        }
    }

    public int getAllocineMovieId(String title, int year, boolean throwTempError) {
        Search search = null;
        try {
            search = allocineApi.searchMovies(title);
        } catch (AllocineException ex) {
            checkTempError(throwTempError, ex);
            LOG.error("Failed retrieving Allocine id for movie '{}': {}", title, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        
        if (search == null || !search.isValid()) {
            return -1;
        }
        
        // if we have a valid year try to find the first movie that match
        if (search.getTotalResults() > 1 && year > 0) {
            for (Movie movie : search.getMovies()) {
                if (movie != null) {
                    int movieProductionYear = movie.getProductionYear();
                    if (movieProductionYear <= 0) {
                        continue;
                    }
                    if (movieProductionYear == year) {
                        return movie.getCode();
                    }
                }
            }
        }
        
        // we don't find a movie or there only one result, return the first
        if (!search.getMovies().isEmpty()) {
            Movie movie = search.getMovies().get(0);
            if (movie != null) {
                return movie.getCode();
            }
        }
        
        // no id found
        return -1;
    }

    public int getAllocineSeriesId(String title, int year, boolean throwTempError) {
        Search search = null;
        try {
            search = allocineApi.searchTvSeries(title);
        } catch (AllocineException ex) {
            checkTempError(throwTempError, ex);
            LOG.error("Failed retrieving Allocine id for series '{}': {}", title, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        
        if (search == null || !search.isValid()) {
            return -1;
        }

        // if we have a valid year try to find the first series that match
        if (search.getTotalResults() > 1 && year > 0) {
            for (TvSeries serie : search.getTvSeries()) {
                if (serie != null) {
                    int serieStart = serie.getYearStart();
                    if (serieStart <= 0) {
                        continue;
                    }
                    int serieEnd = serie.getYearEnd();
                    if (serieEnd <= 0) {
                        serieEnd = serieStart;
                    }
                    if (year >= serieStart && year <= serieEnd) {
                        return serie.getCode();
                    }
                }
            }
        }
        
        // we don't find a series or there only one result, return the first
        if (!search.getTvSeries().isEmpty()) {
            TvSeries serie = search.getTvSeries().get(0);
            if (serie != null) {
                return serie.getCode();
            }
        }
        
        // no id found
        return -1;
    }

    public int getAllocinePersonId(String name, boolean throwTempError) {
        Search search = null;
        try {
            search = allocineApi.searchPersons(name);
        } catch (AllocineException ex) {
            checkTempError(throwTempError, ex);
            LOG.error("Failed retrieving Allocine id for person '{}': {}", name, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        
        if (search == null || !search.isValid()) {
            return -1;
        }
        
        // find for matching person
        if (search.getTotalResults() > 1) {
            for (ShortPerson person : search.getPersons()) {
                if (person != null) {
                    // find exact name (ignoring case)
                    if (StringUtils.equalsIgnoreCase(name, person.getName())) {
                        return person.getCode();
                    }
                }
            }
        }
        
        // we don't find a person or there only one result, return the first
        if (!search.getPersons().isEmpty()) {
            ShortPerson person = search.getPersons().get(0);
            if (person != null) {
                return person.getCode();
            }
        }
        
        // no id found
        return -1;
    }

    public MovieInfos getMovieInfos(String allocineId, boolean throwTempError) {
        final String cacheKey = "movie###"+allocineId;
        MovieInfos movieInfos = cache.get(cacheKey, MovieInfos.class);
        if (movieInfos == null) {
            try {
                movieInfos = allocineApi.getMovieInfos(allocineId);
            } catch (AllocineException ex) {
                checkTempError(throwTempError, ex);
                LOG.error("Failed retrieving Allocine infos for movie id {}: {}", allocineId, ex.getMessage());
                LOG.trace(API_ERROR, ex);
            }
            cache.store(cacheKey, movieInfos);
        }
        return movieInfos;
    }

    public TvSeriesInfos getTvSeriesInfos(String allocineId, boolean throwTempError) {
        final String cacheKey = "series###"+allocineId;
        TvSeriesInfos tvSeriesInfos  = cache.get(cacheKey, TvSeriesInfos.class);
        if (tvSeriesInfos == null) {
            try {
                tvSeriesInfos = allocineApi.getTvSeriesInfos(allocineId);
            } catch (AllocineException ex) {
                checkTempError(throwTempError, ex);
                LOG.error("Failed retrieving Allocine infos for series id {}: {}", allocineId, ex.getMessage());
                LOG.trace(API_ERROR, ex);
            }
            cache.store(cacheKey, tvSeriesInfos);
        }
        return tvSeriesInfos;
    }

    public TvSeasonInfos getTvSeasonInfos(String allocineId) {
        final String cacheKey = "season###"+allocineId;
        TvSeasonInfos tvSeasonInfos = cache.get(cacheKey, TvSeasonInfos.class);
        if (tvSeasonInfos == null) {
            try {
                tvSeasonInfos = allocineApi.getTvSeasonInfos(allocineId);
            } catch (AllocineException ex) {
                LOG.error("Failed retrieving Allocine infos for season id {}: {}", allocineId, ex.getMessage());
                LOG.trace(API_ERROR, ex);
            }
            cache.store(cacheKey, tvSeasonInfos);
        }
        return tvSeasonInfos;
    }

    public EpisodeInfos getEpisodeInfos(String allocineId) {
        EpisodeInfos episodeInfos = null;
        if (StringUtils.isNotBlank(allocineId)) {
            try {
                episodeInfos = allocineApi.getEpisodeInfos(allocineId);
            } catch (AllocineException ex) {
                LOG.error("Failed retrieving Allocine infos for episode id {}: {}", allocineId, ex.getMessage());
                LOG.trace(API_ERROR, ex);
            }
        }
        return episodeInfos;
    }

    public PersonInfos getPersonInfos(String allocineId, boolean throwTempError) {
        final String cacheKey = "person###"+allocineId;
        PersonInfos personInfos = cache.get(cacheKey, PersonInfos.class);
        if (personInfos == null) {
            try {
                personInfos = allocineApi.getPersonInfos(allocineId);
            } catch (AllocineException ex) {
                checkTempError(throwTempError, ex);
                LOG.error("Failed retrieving Allocine infos for person id {}: {}", allocineId, ex.getMessage());
                LOG.trace(API_ERROR, ex);
            }
            cache.store(cacheKey, personInfos);
        }
        return personInfos;
    }

    public FilmographyInfos getFilmographyInfos(String allocineId, boolean throwTempError) {
        FilmographyInfos filmographyInfos = null;
        try {
            filmographyInfos = allocineApi.getPersonFilmography(allocineId);
        } catch (AllocineException ex) {
            checkTempError(throwTempError, ex);
            LOG.error("Failed retrieving Allocine filmography for person id {}: {}", allocineId, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        return filmographyInfos;
    }
}   
