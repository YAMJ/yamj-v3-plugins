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
package org.yamj.plugin.themoviedb;

import static org.yamj.plugin.api.Constants.LANGUAGE_EN;

import com.omertron.themoviedbapi.Compare;
import com.omertron.themoviedbapi.MovieDbException;
import com.omertron.themoviedbapi.TheMovieDbApi;
import com.omertron.themoviedbapi.enumeration.SearchType;
import com.omertron.themoviedbapi.model.artwork.Artwork;
import com.omertron.themoviedbapi.model.collection.Collection;
import com.omertron.themoviedbapi.model.credits.CreditBasic;
import com.omertron.themoviedbapi.model.movie.MovieInfo;
import com.omertron.themoviedbapi.model.person.PersonCreditList;
import com.omertron.themoviedbapi.model.person.PersonFind;
import com.omertron.themoviedbapi.model.person.PersonInfo;
import com.omertron.themoviedbapi.model.tv.*;
import com.omertron.themoviedbapi.results.ResultList;
import com.omertron.themoviedbapi.tools.MethodSub;
import java.net.URL;
import java.util.Locale;
import net.sf.ehcache.Cache;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.tools.ResponseTools;
import org.yamj.plugin.api.service.PluginConfigService;
import org.yamj.plugin.api.tools.EhCacheWrapper;
import org.yamj.plugin.api.web.TemporaryUnavailableException;

public class TheMovieDbApiWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(TheMovieDbApiWrapper.class);
    private static final String API_ERROR = "TheMovieDb error";
    protected static final String NO_LANGUAGE = StringUtils.EMPTY;
                    
    private final TheMovieDbApi tmdbApi;
    private final PluginConfigService configService;
    private final EhCacheWrapper cache;
    
    public TheMovieDbApiWrapper(TheMovieDbApi tmdbApi, PluginConfigService configService, Cache cache) {
        this.tmdbApi = tmdbApi;
        this.configService = configService;
        this.cache = new EhCacheWrapper(cache);
    }
    
    protected TheMovieDbApi getTheMovieDbApi() {
        return tmdbApi;
    }
    
    public int getMovieId(String title, int year, Locale locale, boolean throwTempError) { //NOSONAR
        boolean includeAdult = configService.getBooleanProperty("themoviedb.include.adult", false);
        int searchMatch = configService.getIntProperty("themoviedb.searchMatch", 3);
        
        MovieInfo movie = null;
        try {
            // Search using movie name
            ResultList<MovieInfo> movieList = tmdbApi.searchMovie(title, 0, locale.getLanguage(), includeAdult, year, 0, null);
            LOG.info("Found {} potential matches for {} ({})", movieList.getResults().size(), title, year);
            // Iterate over the list until we find a match
            for (MovieInfo m : movieList.getResults()) {
                String relDate;
                if (StringUtils.isNotBlank(m.getReleaseDate()) && m.getReleaseDate().length() > 4) {
                    relDate = m.getReleaseDate().substring(0, 4);
                } else {
                    relDate = "";
                }
                
                LOG.debug("Checking {} ({})", m.getTitle(), relDate);
                if (Compare.movies(m, title, String.valueOf(year), searchMatch)) {
                    movie = m;
                    break;
                }
            }
        } catch (MovieDbException ex) {
            checkTempError(throwTempError, ex);
            LOG.error("Failed retrieving TMDb id for movie '{}': {}", title, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }

        if (movie != null && movie.getId() != 0) {
            LOG.info("TMDB ID found {} for '{}'", movie.getId(), title);
            return movie.getId();
        }
        return -1;
    }

    public int getSeriesId(String title, int year, Locale locale, boolean throwTempError) { //NOSONAR
        int id = -1;
        TVBasic closestTV = null;
        int closestMatch = Integer.MAX_VALUE;
        boolean foundTV = false;

        try {
            // Search using movie name
            ResultList<TVBasic> seriesList = tmdbApi.searchTV(title, 0, locale.getLanguage(), year, null);
            LOG.info("Found {} potential matches for {} ({})", seriesList.getResults().size(), title, year);
            // Iterate over the list until we find a match
            for (TVBasic tv : seriesList.getResults()) {
                if (title.equalsIgnoreCase(tv.getName())) {
                    id = tv.getId();
                    foundTV = true;
                    break;
                }
                
                LOG.trace("{}: Checking against '{}'", title, tv.getName());
                int lhDistance = StringUtils.getLevenshteinDistance(title, tv.getName());
                LOG.trace("{}: Current closest match is {}, this match is {}", title, closestMatch, lhDistance);
                if (lhDistance < closestMatch) {
                    LOG.trace("{}: TMDB ID {} is a better match ", title, tv.getId());
                    closestMatch = lhDistance;
                    closestTV = tv;
                }
            }

            if (foundTV) {
                LOG.debug("{}: Matched against TMDB ID: {}", title, id);
            } else if (closestMatch < Integer.MAX_VALUE && closestTV != null) {
                id = closestTV.getId();
                LOG.debug("{}: Closest match is '{}' differing by {} characters", title, closestTV.getName(), closestMatch);
            } else {
                LOG.debug("{}: No match found", title);
            }
        } catch (MovieDbException ex) {
            checkTempError(throwTempError, ex);
            LOG.error("Failed retrieving TMDb id for series '{}': {}", title, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        
        return id;
    }
    
    public int getPersonId(String name, boolean throwTempError) { //NOSONAR
        boolean includeAdult = configService.getBooleanProperty("themoviedb.includeAdult", false);

        int id = -1;
        PersonFind closestPerson = null;
        int closestMatch = Integer.MAX_VALUE;
        boolean foundPerson = false;

        try {
            ResultList<PersonFind> results = tmdbApi.searchPeople(name, 0, includeAdult, SearchType.PHRASE);
            LOG.info("{}: Found {} results", name, results.getResults().size());
            for (PersonFind person : results.getResults()) {
                if (name.equalsIgnoreCase(person.getName())) {
                    id = person.getId();
                    foundPerson = true;
                    break;
                }
                
                LOG.trace("{}: Checking against '{}'", name, person.getName());
                int lhDistance = StringUtils.getLevenshteinDistance(name, person.getName());
                LOG.trace("{}: Current closest match is {}, this match is {}", name, closestMatch, lhDistance);
                if (lhDistance < closestMatch) {
                    LOG.trace("{}: TMDB ID {} is a better match ", name, person.getId());
                    closestMatch = lhDistance;
                    closestPerson = person;
                }
            }

            if (foundPerson) {
                LOG.debug("{}: Matched against TMDB ID: {}", name, id);
            } else if (closestMatch < Integer.MAX_VALUE && closestPerson != null) {
                id = closestPerson.getId();
                LOG.debug("{}: Closest match is '{}' differing by {} characters", name, closestPerson.getName(), closestMatch);
            } else {
                LOG.debug("{}: No match found", name);
            }
        } catch (MovieDbException ex) {
            checkTempError(throwTempError, ex);
            LOG.error("Failed retrieving TMDb id for person '{}': {}", name, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        
        return id;
    }

    public PersonInfo getPersonInfo(int tmdbId, boolean throwTempError) {
        PersonInfo personInfo = null;
        try {
            personInfo = tmdbApi.getPersonInfo(tmdbId, MethodSub.COMBINED_CREDITS.getValue());
        } catch (MovieDbException ex) {
            checkTempError(throwTempError, ex);
            LOG.error("Failed to get person info using TMDb ID {}: {}", tmdbId, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        return personInfo;
    }

    public MovieInfo getMovieInfoByTMDB(int tmdbId, Locale locale, boolean throwTempError) {
        MovieInfo movieInfo = null;
        try {
            movieInfo = tmdbApi.getMovieInfo(tmdbId, locale.getLanguage(), MethodSub.RELEASES.getValue(), MethodSub.CREDITS.getValue());
        } catch (MovieDbException ex) {
            checkTempError(throwTempError, ex);
            LOG.error("Failed to get movie info using TMDb ID {}: {}", tmdbId, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        return movieInfo;
    }

    public TVInfo getSeriesInfo(int tmdbId, Locale locale, boolean throwTempError) {
        TVInfo tvInfo = null;
        try {
            tvInfo = tmdbApi.getTVInfo(tmdbId, locale.getLanguage(), MethodSub.EXTERNAL_IDS.getValue());
        } catch (MovieDbException ex) {
            checkTempError(throwTempError, ex);
            LOG.error("Failed to get series info using TMDb ID {}: {}", tmdbId, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        return tvInfo;
    }

    public TVSeasonInfo getSeasonInfo(String tmdbId, int season, Locale locale) {
        TVSeasonInfo tvSeasonInfo = null;
        if (StringUtils.isNumeric(tmdbId)) {
            try {
                tvSeasonInfo = tmdbApi.getSeasonInfo(Integer.parseInt(tmdbId), season, locale.getLanguage());
            } catch (MovieDbException ex) {
                LOG.error("Failed to get episodes using TMDb ID {} and season {}: {}", tmdbId, season, ex.getMessage());
                LOG.trace(API_ERROR, ex);
            }
        }
        return tvSeasonInfo;
    }

    public TVEpisodeInfo getEpisodeInfo(String tmdbId, int season, int episode, Locale locale) {
        TVEpisodeInfo tvEpisodeInfo = null;
        if (StringUtils.isNumeric(tmdbId)) {
            try {
                tvEpisodeInfo = tmdbApi.getEpisodeInfo(Integer.parseInt(tmdbId), season, episode, locale.getLanguage(), MethodSub.CREDITS.getValue(), MethodSub.EXTERNAL_IDS.getValue());
            } catch (MovieDbException ex) {
                LOG.error("Failed to get episodes using TMDb ID {} and season {}: {}", tmdbId, season, ex.getMessage());
                LOG.trace(API_ERROR, ex);
            }
        }
        return tvEpisodeInfo;
    }

    public MovieInfo getMovieInfoByIMDB(String imdbId, Locale locale, boolean throwTempError) {
        MovieInfo movieInfo = null;
        try {
            movieInfo = tmdbApi.getMovieInfoImdb(imdbId, locale.getLanguage(), MethodSub.RELEASES.getValue(), MethodSub.CREDITS.getValue());
        } catch (MovieDbException ex) {
            checkTempError(throwTempError, ex);
            LOG.error("Failed to get movie info using IMDb ID {}: {}", imdbId, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        return movieInfo;
    }

    public PersonCreditList<CreditBasic> getPersonCredits(int tmdbId, Locale locale, boolean throwTempError) {
        try {
            return tmdbApi.getPersonCombinedCredits(tmdbId, locale.getLanguage());
        } catch (MovieDbException ex) {
            checkTempError(throwTempError, ex);
            LOG.error("Failed to get filmography for TMDb ID {}: {}", tmdbId, ex.getMessage());
            LOG.trace(API_ERROR, ex);
            return null;
        }
    }
    
    private static void checkTempError(boolean throwTempError, MovieDbException ex) {
        if (throwTempError && ResponseTools.isTemporaryError(ex)) {
            throw new TemporaryUnavailableException("TheMovieDb service temporary not available: " + ex.getResponseCode(), ex);
        }
    }
    
    public Collection findCollection(String name, String language) {
        try {
            ResultList<Collection> resultList = tmdbApi.searchCollection(name, 0, language);
            if (resultList.isEmpty() && !StringUtils.equalsIgnoreCase(language, LANGUAGE_EN)) {
                resultList = tmdbApi.searchCollection(name, 0, LANGUAGE_EN);
            }

            for (Collection collection : resultList.getResults()) {
                if (StringUtils.isBlank(collection.getTitle())) {
                    continue;
                }

                // 1. check name
                if (StringUtils.equalsIgnoreCase(name, collection.getTitle())) {
                    // found matching collection
                    return collection;
                }

                
                // 2. TODO find matching collection based on the collection members (not supported by TMDbApi until now)
            }
        } catch (MovieDbException ex) {
            LOG.error("Failed retrieving collection for boxed set: {}", name);
            LOG.warn(API_ERROR, ex);
        }
        
        return null;
    }
    
    public URL createImageURL(Artwork artwork, String requiredSize) throws MovieDbException {
        return tmdbApi.createImageUrl(artwork.getFilePath(), requiredSize);
    }

    public ResultList<Artwork> getMovieImages(int tmdbId) {
        try {
            final String cacheKey = "movie###"+tmdbId;
            ResultList<Artwork> resultList = cache.get(cacheKey, ResultList.class);
            if (resultList == null || resultList.isEmpty()) {
                // use an empty language to get all artwork and then filter it
                resultList = tmdbApi.getMovieImages(tmdbId, NO_LANGUAGE);
                cache.store(cacheKey, resultList);
            }
            return resultList;
        } catch (MovieDbException ex) {
            LOG.error("Failed to get movie images for TMDb ID {}: {}", tmdbId, ex.getMessage());
            LOG.trace(API_ERROR, ex);
            return null;
        }
    }

    public ResultList<Artwork> getSeriesImages(int tmdbId) {
        try {
            final String cacheKey = "series###"+tmdbId;
            ResultList<Artwork> resultList = cache.get(cacheKey, ResultList.class);
            if (resultList == null || resultList.isEmpty()) {
                // use an empty language to get all artwork and then filter it
                resultList = tmdbApi.getTVImages(tmdbId, NO_LANGUAGE);
                cache.store(cacheKey, resultList);
            }
            return resultList;
        } catch (MovieDbException ex) {
            LOG.error("Failed to get series images for TMDb ID {}: {}", tmdbId, ex.getMessage());
            LOG.trace(API_ERROR, ex);
            return null;
        }
    }

    public ResultList<Artwork> getSeasonImages(int tmdbId, int season) {
        try {
            final String cacheKey = "season###"+tmdbId+"###"+season;
            ResultList<Artwork> resultList = cache.get(cacheKey, ResultList.class);
            if (resultList == null || resultList.isEmpty()) {
                // use an empty language to get all artwork and then filter it
                resultList = tmdbApi.getSeasonImages(tmdbId, season, NO_LANGUAGE);
                cache.store(cacheKey, resultList);
            }
            return resultList;
        } catch (MovieDbException ex) {
            LOG.error("Failed to get season images for TMDb ID {} and season {}: {}", tmdbId, season, ex.getMessage());
            LOG.trace(API_ERROR, ex);
            return null;
        }
    }

    public ResultList<Artwork> getEpisodeImages(int tmdbId, int season, int episode) {
        try {
            final String cacheKey = "episode###"+tmdbId+"###"+season+"###"+episode;
            ResultList<Artwork> resultList = cache.get(cacheKey, ResultList.class);
            if (resultList == null || resultList.isEmpty()) {
                // use an empty language to get all artwork and then filter it
                resultList = tmdbApi.getEpisodeImages(tmdbId, season, episode);
                cache.store(cacheKey, resultList);
            }
            return resultList;
        } catch (MovieDbException ex) {
            LOG.error("Failed to get episode images for TMDb ID {} and season {} and episode: {}", tmdbId, season, episode, ex.getMessage());
            LOG.trace(API_ERROR, ex);
            return null;
        }
    }

    public ResultList<Artwork> getPersonImages(int tmdbId) {
        try {
            return tmdbApi.getPersonImages(tmdbId);
        } catch (MovieDbException ex) {
            LOG.error("Failed to get person images for TMDb ID {}: {}", tmdbId, ex.getMessage());
            LOG.trace(API_ERROR, ex);
            return null;
        }
    }
    
    public ResultList<Artwork> getCollectionImages(int tmdbId) {
        try {
            final String cacheKey = "boxset###"+tmdbId;
            ResultList<Artwork> resultList = cache.get(cacheKey, ResultList.class);
            if (resultList == null || resultList.isEmpty()) {
                // use an empty language to get all artwork and then filter it
                resultList = tmdbApi.getCollectionImages(tmdbId, NO_LANGUAGE);
                cache.store(cacheKey, resultList);
            }
            return resultList;
        } catch (MovieDbException ex) {
            LOG.error("Failed to get collection images for TMDb ID {}: {}", tmdbId, ex.getMessage());
            LOG.trace(API_ERROR, ex);
            return null;
        }
    }
}
