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

import static org.yamj.plugin.api.service.Constants.SOURCE_IMDB;
import static org.yamj.plugin.api.service.Constants.SOURCE_TMDB;

import com.omertron.themoviedbapi.model.Genre;
import com.omertron.themoviedbapi.model.collection.Collection;
import com.omertron.themoviedbapi.model.credits.MediaCreditCast;
import com.omertron.themoviedbapi.model.credits.MediaCreditCrew;
import com.omertron.themoviedbapi.model.movie.*;
import java.util.Date;
import java.util.HashSet;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.metadata.IMovie;
import org.yamj.plugin.api.metadata.MetadataTools;
import org.yamj.plugin.api.metadata.MovieScanner;
import org.yamj.plugin.api.type.JobType;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class TheMovieDbMovieScanner extends AbstractTheMovieDbScanner implements MovieScanner {

    private static final Logger LOG = LoggerFactory.getLogger(TheMovieDbMovieScanner.class);

    @Override
    public boolean isValidMovieId(String movieId) {
        return isValidTheMovieDbId(movieId);
    }

    @Override
    public boolean scanMovie(IMovie movie, boolean throwTempError) {
        // get movie id
        final String tmdbId = movie.getId(SOURCE_TMDB);
        if (isNoValidTheMovieDbId(tmdbId)) {
            LOG.debug("TheMovieDb id not available '{}'", movie.getTitle());
            return false;
        }
        
        // get movie info
        MovieInfo movieInfo = theMovieDbApiWrapper.getMovieInfoByTMDB(Integer.parseInt(tmdbId), locale, throwTempError);
        if (movieInfo == null || movieInfo.getId() <= 0) {
            LOG.error("Can't find informations for movie '{}'", movie.getTitle());
            return false;
        }
                        
        movie.addId(SOURCE_IMDB, movieInfo.getImdbID());
        movie.setTitle(movieInfo.getTitle());
        movie.setOriginalTitle(movieInfo.getOriginalTitle());
        movie.setYear(MetadataTools.extractYearAsInt(movieInfo.getReleaseDate()));
        movie.setPlot(movieInfo.getOverview());
        movie.setOutline(movieInfo.getOverview());
        movie.setTagline(movieInfo.getTagline());
        movie.setRating(MetadataTools.parseRating(movieInfo.getVoteAverage()));

        // RELEASE DATE
        Date releaseDate = null;
        if (CollectionUtils.isNotEmpty(movieInfo.getReleases())) {
            for (ReleaseInfo releaseInfo : movieInfo.getReleases()) {
                if (locale.getCountry().equalsIgnoreCase(releaseInfo.getCountry())) {
                    releaseDate = parseTMDbDate(releaseInfo.getReleaseDate());
                    if (releaseDate != null) {
                        movie.setRelease(releaseInfo.getCountry(), releaseDate);
                    }
                    break;
                }
            }
            if (releaseDate == null) {
                // use primary release date
                for (ReleaseInfo releaseInfo : movieInfo.getReleases()) {
                    if (releaseInfo.isPrimary()) {
                        releaseDate = parseTMDbDate(releaseInfo.getReleaseDate());
                        if (releaseDate != null) {
                            movie.setRelease(releaseInfo.getCountry(), releaseDate);
                        }
                        break;
                    }
                }
            }
        }
        if (releaseDate == null) {
            movie.setRelease(null, parseTMDbDate(movieInfo.getReleaseDate()));
        }
        
        // CERTIFICATIONS
        if (CollectionUtils.isNotEmpty(movieInfo.getReleases())) {
            for (String countryCode : localeService.getCertificationCountryCodes(locale)) {
                relLoop: for (ReleaseInfo releaseInfo : movieInfo.getReleases()) {
                    if (countryCode.equalsIgnoreCase(releaseInfo.getCountry())) {
                        movie.addCertification(releaseInfo.getCountry(), releaseInfo.getCertification());
                        break relLoop;
                    }
                }
            }
        }
        
        // COUNTRIES
        if (CollectionUtils.isNotEmpty(movieInfo.getProductionCountries())) {
            final HashSet<String> countries = new HashSet<>(movieInfo.getProductionCountries().size());
            for (ProductionCountry productionCountry : movieInfo.getProductionCountries()) {
                countries.add(productionCountry.getCountry());
            }
            movie.setCountries(countries);
        }

        // GENRES
        if (CollectionUtils.isNotEmpty(movieInfo.getGenres())) {
            final HashSet<String> genres = new HashSet<>(movieInfo.getGenres().size());
            for (Genre genre : movieInfo.getGenres()) {
                genres.add(genre.getName());
            }
            movie.setGenres(genres);
        }

        // COMPANIES
        if (CollectionUtils.isNotEmpty(movieInfo.getProductionCompanies())) {
            final HashSet<String> studios = new HashSet<>(movieInfo.getProductionCompanies().size());
            for (ProductionCompany company : movieInfo.getProductionCompanies()) {
                if (StringUtils.isNotBlank(company.getName())) {
                    studios.add(StringUtils.trim(company.getName()));
                }
            }
            movie.setStudios(studios);
        }

        // CAST
        if (configService.isCastScanEnabled(JobType.ACTOR)) {
            final boolean skipUncredited = configService.getBooleanProperty("themoviedb.castcrew.skip.uncredited", true);
            
            for (MediaCreditCast person : movieInfo.getCast()) {
                // skip person without credit
                if (skipUncredited && StringUtils.indexOf(person.getCharacter(), "uncredited") > 0) {
                    continue;
                }
                movie.addCredit(String.valueOf(person.getId()), JobType.ACTOR, person.getName(), person.getCharacter());
            }
        }

        // CREW
        for (MediaCreditCrew person : movieInfo.getCrew()) {
            JobType jobType = retrieveJobType(person.getName(), person.getDepartment());
            if (!configService.isCastScanEnabled(jobType)) {
                // scan not enabled for that job
                continue;
            }
            movie.addCredit(String.valueOf(person.getId()), jobType, person.getName(), person.getJob());
        }

        // store collection as boxed set
        if (configService.getBooleanProperty("themoviedb.include.collection", false)) {
            Collection collection = movieInfo.getBelongsToCollection();
            if (collection != null) {
                movie.addCollection(collection.getName(), Integer.toString(collection.getId()));
            }
        }
        
        return true;
    }
}
