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

import static org.yamj.plugin.api.Constants.SOURCE_IMDB;
import static org.yamj.plugin.api.Constants.SOURCE_TMDB;

import com.omertron.themoviedbapi.model.credits.CreditBasic;
import com.omertron.themoviedbapi.model.credits.CreditMovieBasic;
import com.omertron.themoviedbapi.model.person.PersonCreditList;
import com.omertron.themoviedbapi.model.person.PersonInfo;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.metadata.*;
import org.yamj.plugin.api.model.IPerson;
import org.yamj.plugin.api.model.type.JobType;
import org.yamj.plugin.api.model.type.ParticipationType;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class TheMovieDbPersonScanner extends AbstractTheMovieDbScanner implements PersonScanner, FilmographyScanner {

    private static final Logger LOG = LoggerFactory.getLogger(TheMovieDbPersonScanner.class);

    @Override
    public boolean isValidPersonId(String personId) {
        return isValidTheMovieDbId(personId);
    }

    @Override
    public boolean scanPerson(IPerson person, boolean throwTempError) {
        // get person id
        final String tmdbId = person.getId(SOURCE_TMDB);
        if (isNoValidTheMovieDbId(tmdbId)) {
            LOG.debug("TheMovieDb id not available '{}'", person.getName());
            return false;
        }

        // get person info
        PersonInfo tmdbPerson = theMovieDbApiWrapper.getPersonInfo(Integer.parseInt(tmdbId), throwTempError);
        if (tmdbPerson == null || tmdbPerson.getId() <= 0) {
            LOG.error("Can't find information for person '{}'", person.getName());
            return false;
        }

        // fill in data
        person.addId(SOURCE_IMDB, StringUtils.trim(tmdbPerson.getImdbId()));
        person.setName(tmdbPerson.getName());
        person.setBirthDay(MetadataTools.parseToDate(tmdbPerson.getBirthday()));
        person.setBirthPlace(StringUtils.trimToNull(tmdbPerson.getPlaceOfBirth()));
        person.setDeathDay(MetadataTools.parseToDate(tmdbPerson.getDeathday()));
        person.setBiography(MetadataTools.cleanBiography(tmdbPerson.getBiography()));

        if (CollectionUtils.isNotEmpty(tmdbPerson.getAlsoKnownAs())) {
            person.setBirthName(tmdbPerson.getAlsoKnownAs().get(0));
        }
        
        return true;
    }

    @Override
    public List<FilmographyDTO> scanFilmography(String tmdbId, boolean throwTempError) {
        PersonCreditList<CreditBasic> credits = theMovieDbApiWrapper.getPersonCredits(Integer.parseInt(tmdbId), localeService.getLocale(), throwTempError);
        if (credits == null || CollectionUtils.isEmpty(credits.getCast())) {
            LOG.trace("No filmography found for person ID {}", tmdbId);
            return null;
        }

        // Fill in cast data
        List<FilmographyDTO> result = new ArrayList<>();
        for (CreditBasic credit : credits.getCast()) {
            FilmographyDTO filmo = null;
            switch (credit.getMediaType()) {
                case MOVIE:
                    filmo = convertMovieCreditToFilm((CreditMovieBasic) credit, JobType.ACTOR);
                    break;
                case TV:
                    LOG.trace("TV credit information for {} not used: {}", JobType.ACTOR, credit);
                    break;
                case EPISODE:
                    LOG.trace("TV episode credit information for {} not used: {}", JobType.ACTOR, credit);
                    break;
                default:
                    LOG.debug("Unknown media type '{}' for credit: {}", credit.getMediaType(), credit);
            }

            if (filmo != null) {
                result.add(filmo);
            }
        }

        // Fill in CREW data
        for (CreditBasic credit : credits.getCrew()) {
            final JobType jobType = retrieveJobType(credit.getDepartment());
            FilmographyDTO filmo = null;
            switch (credit.getMediaType()) {
                case MOVIE:
                    filmo = convertMovieCreditToFilm((CreditMovieBasic) credit, jobType);
                    break;
                case TV:
                    LOG.trace("TV crew information for {} not used: {}", jobType, credit);
                    break;
                case EPISODE:
                    LOG.trace("TV episode crew information for {} not used: {}", jobType, credit);
                    break;
                default:
                    LOG.debug("Unknown crew media type '{}' for credit: {}", credit.getMediaType(), credit);
            }

            if (filmo != null) {
                result.add(filmo);
            }
        }
        
        return result;
    }

    private static FilmographyDTO convertMovieCreditToFilm(CreditMovieBasic credit, JobType jobType) {
        Date releaseDate = MetadataTools.parseToDate(credit.getReleaseDate());
        if (releaseDate == null) {
            // release date must be present
            return null;
        }

        FilmographyDTO filmo = new FilmographyDTO();
        filmo.setParticipationType(ParticipationType.MOVIE);
        filmo.setId(String.valueOf(credit.getId()));
        filmo.setJobType(jobType);
        if (JobType.ACTOR == jobType) {
            filmo.setRole(MetadataTools.cleanRole(credit.getCharacter()));
            filmo.setVoiceRole(MetadataTools.isVoiceRole(credit.getCharacter()));
        } else {
            filmo.setRole(credit.getJob());
        }
        filmo.setTitle(credit.getTitle());
        filmo.setOriginalTitle(StringUtils.trimToNull(credit.getOriginalTitle()));
        filmo.setReleaseDate(releaseDate);
        filmo.setYear(MetadataTools.extractYearAsInt(releaseDate));
        return filmo;
    }    
}
