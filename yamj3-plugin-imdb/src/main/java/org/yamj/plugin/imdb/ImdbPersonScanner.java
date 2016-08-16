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
package org.yamj.plugin.imdb;

import static org.yamj.plugin.api.Constants.SOURCE_IMDB;

import com.omertron.imdbapi.model.ImdbFilmography;
import com.omertron.imdbapi.model.ImdbMovieCharacter;
import com.omertron.imdbapi.model.ImdbPerson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.metadata.*;
import org.yamj.plugin.api.model.IPerson;
import org.yamj.plugin.api.model.type.JobType;
import org.yamj.plugin.api.model.type.ParticipationType;
import org.yamj.plugin.api.web.HTMLTools;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class ImdbPersonScanner extends AbstractImdbScanner implements PersonScanner, FilmographyScanner {

    private static final Logger LOG = LoggerFactory.getLogger(ImdbPersonScanner.class);

    @Override
    public boolean isValidPersonId(String personId) {
        return isValidImdbId(personId);
    }

    @Override
    public boolean scanPerson(IPerson person, boolean throwTempError) {
        // get person id
        final String imdbId = person.getId(SOURCE_IMDB);
        if (isNoValidImdbId(imdbId)) {
            LOG.debug("IMDb id not available : {}", person.getName());
            return false;
        }

        try {
            LOG.debug("IMDb id available ({}), updating person", imdbId);
            return updatePerson(person, imdbId, throwTempError);
            
        } catch (IOException ioe) {
            LOG.error("IMDb service error: '" + person.getName() + "'", ioe);
            return false;
        }
    }

    private boolean updatePerson(IPerson person, String imdbId, boolean throwTempError) throws IOException {
        Locale locale = localeService.getLocale();
        ImdbPerson imdbPerson = imdbApiWrapper.getPerson(imdbId, locale, throwTempError);
        if (imdbPerson == null || StringUtils.isBlank(imdbPerson.getActorId())) {
            return false;
        }
        
        person.setName(imdbPerson.getName());
        person.setBirthName(imdbPerson.getRealName());
        
        final String apiBio = MetadataTools.cleanBiography(imdbPerson.getBiography());
        if (StringUtils.isNotBlank(apiBio)) {
            person.setBiography(apiBio);
        } else {
            // try biography from web site
            final String bio = imdbApiWrapper.getPersonBioXML(imdbId, throwTempError);
            if (bio.contains(">Mini Bio (1)</h4>")) {
                String biography = HTMLTools.extractTag(bio, ">Mini Bio (1)</h4>", "<em>- IMDb Mini Biography");
                if (StringUtils.isBlank(biography) && bio.contains("<a name=\"trivia\">")) {
                    biography = HTMLTools.extractTag(bio, ">Mini Bio (1)</h4>", "<a name=\"trivia\">");
                }
                person.setBiography(HTMLTools.removeHtmlTags(biography));
            }
        }
        
        if (imdbPerson.getBirth() != null) {
            if (imdbPerson.getBirth().getDate() != null) {
                final String birthDay = imdbPerson.getBirth().getDate().get(LITERAL_NORMAL);
                person.setBirthDay(MetadataTools.parseToDate(birthDay));
            }
            person.setBirthPlace(imdbPerson.getBirth().getPlace());
        }

        if (imdbPerson.getDeath() != null) {
            if (imdbPerson.getDeath().getDate() != null) {
                final String deathDay = imdbPerson.getDeath().getDate().get(LITERAL_NORMAL);
                person.setDeathDay(MetadataTools.parseToDate(deathDay));
            }
            person.setDeathPlace(imdbPerson.getDeath().getPlace());
        }

        return true;
    }

    @Override
    public List<FilmographyDTO> scanFilmography(String imdbId, boolean throwTempError) {
        Locale locale = localeService.getLocale();
        List<ImdbFilmography> imdbFilmography = this.imdbApiWrapper.getFilmopgraphy(imdbId, locale, throwTempError);
        if (imdbFilmography == null || imdbFilmography.isEmpty()) {
            return null; //NOSONAR
        }
        
        List<FilmographyDTO> result = new ArrayList<>();
        for (ImdbFilmography imdbFilmo : imdbFilmography) {
            if (StringUtils.isBlank(imdbFilmo.getToken()) || CollectionUtils.isEmpty(imdbFilmo.getList())) {
                // missing info
                continue;
            }
            
            switch (imdbFilmo.getToken()) {
            case "actor":
                actorFilmography(result, imdbFilmo.getList());
                break;
            case "director":
                crewFilmography(JobType.DIRECTOR, result, imdbFilmo.getList());
                break;
            case "writer":
                crewFilmography(JobType.WRITER, result, imdbFilmo.getList());
                break;
            case "editor":
                crewFilmography(JobType.EDITING, result, imdbFilmo.getList());
                break;
            case "producer":
                crewFilmography(JobType.PRODUCER, result, imdbFilmo.getList());
                break;
            case "visualX20effects":
                crewFilmography(JobType.EFFECTS, result, imdbFilmo.getList());
                break;
            case "cinematographer":
                crewFilmography(JobType.CAMERA, result, imdbFilmo.getList());
                break;
            case "productionX20designer":
            case "artX20director":
                crewFilmography(JobType.ART, result, imdbFilmo.getList());
                break;
            case "cameraX20andX20electricalX20department":
            case "miscellaneousX20crew":
            case "secondX20unitX20directorX20orX20assistantX20director":
            case "artX20department":
                crewFilmography(JobType.CREW, result, imdbFilmo.getList());
                break;
            case "thanks":
            case "self":
                // ignore these jobs
                break;
            default:
                LOG.info("Unhandled filmography job type: {}", imdbFilmo.getToken());
                break;
            }
        }
        return result;
    }
        
    private static void actorFilmography(List<FilmographyDTO> result, List<ImdbMovieCharacter> characters) {
        for (ImdbMovieCharacter character : characters) {
            if (character.getTitle() == null) {
                // movie info must be present
                continue;
            }

            if ("feature".equals(character.getTitle().getType())) {
                // MOVIE
                result.add(new FilmographyDTO()
                    .setJobType(JobType.ACTOR)
                    .setParticipationType(ParticipationType.MOVIE)
                    .setId(character.getTitle().getImdbId())
                    .setTitle(character.getTitle().getTitle())
                    .setYear(character.getTitle().getYear())
                    .setReleaseDate(MetadataTools.parseToDate(character.getTitle().getReleaseDate()))
                    .setRole(character.getCharacter())
                    .setVoiceRole(MetadataTools.isVoiceRole(character.getAttribute())));
            }
        }
    }
    
    private static void crewFilmography(JobType jobType, List<FilmographyDTO> result, List<ImdbMovieCharacter> characters) {
        for (ImdbMovieCharacter character : characters) {
            if (character.getTitle() == null) {
                // movie info must be present
                continue;
            }
            
            if ("feature".equals(character.getTitle().getType())) {
                // MOVIE
                result.add(new FilmographyDTO()
                    .setJobType(jobType)
                    .setParticipationType(ParticipationType.MOVIE)
                    .setId(character.getTitle().getImdbId())
                    .setTitle(character.getTitle().getTitle())
                    .setYear(character.getTitle().getYear())
                    .setReleaseDate(MetadataTools.parseToDate(character.getTitle().getReleaseDate())));
            }
        }
    }
}
