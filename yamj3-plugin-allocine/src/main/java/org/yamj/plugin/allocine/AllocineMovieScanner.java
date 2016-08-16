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
package org.yamj.plugin.allocine;

import static org.yamj.plugin.allocine.AllocinePlugin.SCANNER_NAME;

import com.moviejukebox.allocine.model.FestivalAward;
import com.moviejukebox.allocine.model.MovieInfos;
import com.moviejukebox.allocine.model.MoviePerson;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.metadata.MetadataTools;
import org.yamj.plugin.api.metadata.MovieScanner;
import org.yamj.plugin.api.model.IMovie;
import org.yamj.plugin.api.model.type.JobType;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class AllocineMovieScanner extends AbstractAllocineScanner implements MovieScanner {

    private static final Logger LOG = LoggerFactory.getLogger(AllocineMovieScanner.class);

    @Override
    public boolean isValidMovieId(String movieId) {
        return isValidAllocineId(movieId);
    }

    @Override
    public boolean scanMovie(IMovie movie, boolean throwTempError) {
        // get movie id
        final String allocineId = movie.getId(SCANNER_NAME);
        if (isNoValidAllocineId(allocineId)) {
            LOG.debug("Allocine id not available '{}'", movie.getTitle());
            return false;
        }
        
        // get movie info 
        MovieInfos movieInfos = allocineApiWrapper.getMovieInfos(allocineId, throwTempError);
        if (movieInfos == null || movieInfos.isNotValid()) {
            LOG.error("Can't find informations for movie '{}'", movie.getTitle());
            return false;
        }

        movie.setTitle(movieInfos.getTitle());
        movie.setOriginalTitle(movieInfos.getOriginalTitle());
        movie.setYear(movieInfos.getProductionYear());
        movie.setPlot(movieInfos.getSynopsis());
        movie.setOutline(movieInfos.getSynopsisShort());
        movie.setRelease(movieInfos.getReleaseCountry(), MetadataTools.parseToDate(movieInfos.getReleaseDate()));
        movie.setGenres(movieInfos.getGenres());
        movie.setCountries(movieInfos.getNationalities());
        movie.addCertification(Locale.FRANCE.getCountry(), movieInfos.getCertification());
        movie.setRating(movieInfos.getUserRating());

        if (StringUtils.isNotBlank(movieInfos.getDistributor())) {
            Set<String> studios = Collections.singleton(movieInfos.getDistributor());
            movie.setStudios(studios);
        }

        // add actors
        if (configService.isCastScanEnabled(JobType.ACTOR)) {
            for (MoviePerson person : movieInfos.getActors()) {
                addCredit(movie, person, JobType.ACTOR, person.getRole());
            }
        }

        // add other jobs
        addCredits(movie, movieInfos.getDirectors(), JobType.DIRECTOR);
        addCredits(movie, movieInfos.getWriters(), JobType.WRITER);
        addCredits(movie, movieInfos.getProducers(), JobType.PRODUCER);
        addCredits(movie, movieInfos.getCamera(), JobType.CAMERA);
        addCredits(movie, movieInfos.getArt(), JobType.ART);

        // add awards
        if (movieInfos.getFestivalAwards() != null && configService.getBooleanProperty("allocine.movie.awards", false)) {
            for (FestivalAward festivalAward : movieInfos.getFestivalAwards()) {
                movie.addAward(festivalAward.getFestival(), festivalAward.getName(), festivalAward.getYear());
            }
        }
        
        return true;
    }

    private void addCredits(IMovie movie, Collection<MoviePerson> persons, JobType jobType) {
        if (!persons.isEmpty() && configService.isCastScanEnabled(jobType)) {
            for (MoviePerson person : persons) {
                addCredit(movie, person, jobType);
            }
        }
    }

    private static void addCredit(IMovie movie, MoviePerson person, JobType jobType) {
        addCredit(movie, person, jobType, null);
    }

    private static void addCredit(IMovie movie, MoviePerson person, JobType jobType, String role) {
        String sourceId = person.getCode() > 0 ? Long.toString(person.getCode()) : null;
        movie.addCredit(sourceId, jobType, person.getName(), role);
    }
}
