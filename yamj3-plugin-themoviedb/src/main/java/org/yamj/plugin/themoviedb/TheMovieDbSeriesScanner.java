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

import static org.yamj.plugin.api.Constants.*;

import com.omertron.themoviedbapi.model.Genre;
import com.omertron.themoviedbapi.model.credits.MediaCreditCast;
import com.omertron.themoviedbapi.model.credits.MediaCreditCrew;
import com.omertron.themoviedbapi.model.media.MediaCreditList;
import com.omertron.themoviedbapi.model.movie.ProductionCompany;
import com.omertron.themoviedbapi.model.tv.TVEpisodeInfo;
import com.omertron.themoviedbapi.model.tv.TVInfo;
import com.omertron.themoviedbapi.model.tv.TVSeasonInfo;
import java.util.*;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.metadata.MetadataTools;
import org.yamj.plugin.api.metadata.SeriesScanner;
import org.yamj.plugin.api.model.IEpisode;
import org.yamj.plugin.api.model.ISeason;
import org.yamj.plugin.api.model.ISeries;
import org.yamj.plugin.api.model.type.JobType;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class TheMovieDbSeriesScanner extends AbstractTheMovieDbScanner implements SeriesScanner {

    private static final Logger LOG = LoggerFactory.getLogger(TheMovieDbSeriesScanner.class);

    @Override
    public boolean isValidSeriesId(String movieId) {
        return isValidTheMovieDbId(movieId);
    }

    @Override
    public boolean scanSeries(ISeries series, boolean throwTempError) {
        // get series id
        final String tmdbId = series.getId(SOURCE_TMDB);
        if (isNoValidTheMovieDbId(tmdbId)) {
            LOG.debug("TMDb id not available '{}'", series.getTitle());
            return false;
        }

        // get series info
        final Locale locale = localeService.getLocale();
        TVInfo tvInfo = theMovieDbApiWrapper.getSeriesInfo(Integer.parseInt(tmdbId), locale, throwTempError);
        if (tvInfo == null || tvInfo.getId() <= 0) {
            LOG.error("Can't find informations for series '{}'", series.getTitle());
            return false;
        }

        // set external IDS
        if (tvInfo.getExternalIDs() != null) {
            series.addId(SOURCE_IMDB, tvInfo.getExternalIDs().getImdbId());
            series.addId(SOURCE_TVDB, tvInfo.getExternalIDs().getTvdbId());
            series.addId(SOURCE_TVRAGE, tvInfo.getExternalIDs().getTvrageId());
        }
        
        series.setTitle(tvInfo.getName());
        series.setOriginalTitle(tvInfo.getOriginalName());
        series.setPlot(tvInfo.getOverview());
        series.setOutline(tvInfo.getOverview());
        series.setCountries(tvInfo.getOriginCountry());

        if (tvInfo.getVoteAverage() > 0) {
            series.setRating(MetadataTools.parseRating(tvInfo.getVoteAverage()));
        }

        if (CollectionUtils.isNotEmpty(tvInfo.getGenres())) {
            final Set<String> genres = new HashSet<>(tvInfo.getGenres().size());
            for (Genre genre :  tvInfo.getGenres()) {
                genres.add(genre.getName());
            }
            series.setGenres(genres);
        }
        
        if (CollectionUtils.isNotEmpty(tvInfo.getProductionCompanies())) {
            final Set<String> studios = new HashSet<>(tvInfo.getProductionCompanies().size());
            for (ProductionCompany company : tvInfo.getProductionCompanies()) {
                studios.add(company.getName());
            }
            series.setStudios(studios);
        }

        // first air date
        Date date = parseTMDbDate(tvInfo.getFirstAirDate());
        series.setStartYear(MetadataTools.extractYearAsInt(date));
        // last air date
        date = parseTMDbDate(tvInfo.getLastAirDate());
        series.setEndYear(MetadataTools.extractYearAsInt(date));

        // SCAN SEASONS
        scanSeasons(series, tvInfo, locale);
        
        return true;
    }
    
    private void scanSeasons(ISeries series, TVInfo tvInfo, Locale locale) {
        for (ISeason season : series.getSeasons()) {

            // nothing to do if season already done
            if (!season.isDone()) {
                final String seriesId = season.getSeries().getId(SOURCE_TMDB);
                TVSeasonInfo seasonInfo = theMovieDbApiWrapper.getSeasonInfo(seriesId, season.getNumber(), locale);
                
                if (seasonInfo == null || seasonInfo.getId() <= 0) {
                    // mark season as not found
                    season.setNotFound();
                } else {
                    // fill in data
                    season.addId(SOURCE_TMDB, String.valueOf(seasonInfo.getId()));
                    season.setTitle(seasonInfo.getName());
                    season.setOriginalTitle(tvInfo.getOriginalName());
                    season.setPlot(seasonInfo.getOverview());
                    season.setOutline(seasonInfo.getOverview());
                    season.setYear(MetadataTools.extractYearAsInt(parseTMDbDate(seasonInfo.getAirDate())));
        
                    // mark season as done
                    season.setDone();
                }
            }
            
            // scan episodes
            scanEpisodes(season, locale);
        }
    }
    
    private void scanEpisodes(ISeason season, Locale locale) {
        for (IEpisode episode : season.getEpisodes()) {
            if (episode.isDone()) {
                // nothing to do anymore
                continue;
            }
            
            // get the episode
            String seriesId = season.getSeries().getId(SOURCE_TMDB);
            TVEpisodeInfo episodeInfo = theMovieDbApiWrapper.getEpisodeInfo(seriesId, season.getNumber(), episode.getNumber(), locale);
            if (episodeInfo == null || episodeInfo.getId() <= 0) {
                // mark episode as not found
                episode.setNotFound();
                continue;
            }
            
            // fill in data
            episode.addId(SOURCE_TMDB, String.valueOf(episodeInfo.getId()));

            // set external IDs
            if (episodeInfo.getExternalIDs() != null) {
                episode.addId(SOURCE_IMDB, episodeInfo.getExternalIDs().getImdbId());
                episode.addId(SOURCE_TVDB, episodeInfo.getExternalIDs().getTvdbId());
                episode.addId(SOURCE_TVRAGE, episodeInfo.getExternalIDs().getTvrageId());
            }

            episode.setTitle(episodeInfo.getName());
            episode.setPlot(episodeInfo.getOverview());
            episode.setOutline(episodeInfo.getOverview());
            episode.setRelease(parseTMDbDate(episodeInfo.getAirDate()));
            episode.setRating(MetadataTools.parseRating(episodeInfo.getVoteAverage()));
            
            // CAST & CREW
            MediaCreditList credits = episodeInfo.getCredits();
            if (credits != null) {
                if (CollectionUtils.isNotEmpty(credits.getCast()) && configService.isCastScanEnabled(JobType.ACTOR)) {
                    for (MediaCreditCast person : credits.getCast()) {
                        episode.addCredit(String.valueOf(person.getId()), JobType.ACTOR, person.getName(), person.getCharacter());
                    }
                }
            
                // GUEST STARS
                if (CollectionUtils.isNotEmpty(credits.getGuestStars()) && configService.isCastScanEnabled(JobType.GUEST_STAR)) {
                    for (MediaCreditCast person : credits.getGuestStars()) {
                        episode.addCredit(String.valueOf(person.getId()), JobType.ACTOR, person.getName(), person.getCharacter());
                    }
                }
            
                // CREW
                if (CollectionUtils.isNotEmpty(credits.getCrew())) {
                    for (MediaCreditCrew person : credits.getCrew()) {
                        final JobType jobType = retrieveJobType(person.getDepartment());
                        if (!configService.isCastScanEnabled(jobType)) {
                            // scan not enabled for that job
                            continue;
                        }
                        episode.addCredit(String.valueOf(person.getId()), jobType, person.getName(), person.getJob());
                    }
                }
            }

            // mark episode as done
            episode.setDone();
        }
    }
}
