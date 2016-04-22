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
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.metadata.*;
import org.yamj.plugin.api.type.JobType;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class AllocineMovieScanner extends AbstractAllocineScanner implements MovieScanner {

    private static final Logger LOG = LoggerFactory.getLogger(AllocineMovieScanner.class);

    @Override
    public boolean isValidMovieId(String movieId) {
        return isValidAllocineId(movieId);
    }

    @Override
    public boolean scanMovie(MovieDTO movie, boolean throwTempError) {
        // get movie id
        final String allocineId = movie.getIds().get(SCANNER_NAME);
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

        movie.setTitle(movieInfos.getTitle())
            .setOriginalTitle(movieInfos.getOriginalTitle())
            .setYear(movieInfos.getProductionYear())
            .setPlot(movieInfos.getSynopsis())
            .setOutline(movieInfos.getSynopsisShort())
            .setReleaseCountry(movieInfos.getReleaseCountry())
            .setReleaseDate(MetadataTools.parseToDate(movieInfos.getReleaseDate()))
            .setGenres(movieInfos.getGenres())
            .addStudio(movieInfos.getDistributor())
            .setCountries(movieInfos.getNationalities())
            .addCertification(Locale.FRANCE.getCountry(), movieInfos.getCertification())
            .setRating(movieInfos.getUserRating());

        // DIRECTORS
        if (configService.isCastScanEnabled(JobType.DIRECTOR)) {
            for (MoviePerson person : movieInfos.getDirectors()) {
                movie.addCredit(createCredit(person, JobType.DIRECTOR));
            }
        }
        
        // WRITERS
        if (configService.isCastScanEnabled(JobType.WRITER)) {
            for (MoviePerson person : movieInfos.getWriters()) {
                movie.addCredit(createCredit(person, JobType.WRITER));
            }
        }
        
        // ACTORS
        if (configService.isCastScanEnabled(JobType.ACTOR)) {
            for (MoviePerson person : movieInfos.getActors()) {
                movie.addCredit(createCredit(person, JobType.ACTOR, person.getRole()));
            }
        }
        
        // CAMERA    
        if (configService.isCastScanEnabled(JobType.CAMERA)) {
            for (MoviePerson person : movieInfos.getCamera()) {
                movie.addCredit(createCredit(person, JobType.CAMERA));
            }
        }
        
        // PRODUCERS        
        if (configService.isCastScanEnabled(JobType.PRODUCER)) {
            for (MoviePerson person : movieInfos.getProducers()) {
                movie.addCredit(createCredit(person, JobType.PRODUCER));
            }
        }

        // add awards
        if (movieInfos.getFestivalAwards() != null && configService.getBooleanProperty("allocine.movie.awards", false)) {
            for (FestivalAward festivalAward : movieInfos.getFestivalAwards()) {
                movie.addAward(new AwardDTO(SCANNER_NAME, festivalAward.getFestival(), festivalAward.getName(), festivalAward.getYear()));
            }
        }
        
        return true;
    }

    private static CreditDTO createCredit(MoviePerson person, JobType jobType) {
        return createCredit(person, jobType, null);
    }

    private static CreditDTO createCredit(MoviePerson person, JobType jobType, String role) {
        String sourceId = (person.getCode() > 0 ?  String.valueOf(person.getCode()) : null);
        return new CreditDTO(SCANNER_NAME, sourceId, jobType, person.getName(), role);
    }
}
