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

import com.moviejukebox.allocine.model.FilmographyInfos;
import com.moviejukebox.allocine.model.Participance;
import com.moviejukebox.allocine.model.PersonInfos;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.metadata.*;
import org.yamj.plugin.api.model.IPerson;
import org.yamj.plugin.api.model.type.JobType;
import org.yamj.plugin.api.model.type.ParticipationType;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class AllocinePersonScanner extends AbstractAllocineScanner implements PersonScanner, FilmographyScanner {

    private static final Logger LOG = LoggerFactory.getLogger(AllocinePersonScanner.class);

    @Override
    public boolean isValidPersonId(String personId) {
        return isValidAllocineId(personId);
    }

    @Override
    public boolean scanPerson(IPerson person, boolean throwTempError) {
        final String allocineId = person.getId(SCANNER_NAME);
        if (isNoValidAllocineId(allocineId)) {
            return false;
        }

        // get person info
        PersonInfos personInfos = allocineApiWrapper.getPersonInfos(allocineId, throwTempError);
        if (personInfos == null || personInfos.isNotValid()) {
            LOG.error("Can't find informations for person '{}'", person.getName());
            return false;
        }
        
        // fill in data
        person.setNames(personInfos.getFullName(), personInfos.getFirstName(), personInfos.getLastName());
        person.setBirthDay(MetadataTools.parseToDate(personInfos.getBirthDate()));
        person.setBirthPlace(personInfos.getBirthPlace());
        person.setBirthName(personInfos.getRealName());
        person.setDeathDay(MetadataTools.parseToDate(personInfos.getDeathDate()));
        person.setDeathPlace(personInfos.getDeathPlace());
        person.setBiography(personInfos.getBiography());
        
        return true;
    }

    @Override
    public List<FilmographyDTO> scanFilmography(String allocineId, boolean throwTempError) {
        FilmographyInfos filmographyInfos = allocineApiWrapper.getFilmographyInfos(allocineId, throwTempError);
        if (filmographyInfos == null || filmographyInfos.isNotValid() || filmographyInfos.getParticipances() == null) {
            LOG.trace("No filmography found for person ID {}", allocineId);
            return null;
        }
        
        List<FilmographyDTO> result = new ArrayList<>();
        for (Participance participance : filmographyInfos.getParticipances()) {
            FilmographyDTO dto = new FilmographyDTO();
            dto.setId(String.valueOf(participance.getCode()));
            
            if (participance.isActor()) {
                dto.setJobType(JobType.ACTOR);
                dto.setRole(participance.getRole());
            } else if (participance.isDirector()) {
                dto.setJobType(JobType.DIRECTOR);
            } else if (participance.isWriter()) {
                dto.setJobType(JobType.WRITER);
            } else if (participance.isCamera()) {
                dto.setJobType(JobType.CAMERA);
            } else if (participance.isProducer()) {
                dto.setJobType(JobType.PRODUCER);
            } else {
                // no entries with unknown job type
                continue;
            }

            if (participance.isTvShow()) {
                dto.setParticipationType(ParticipationType.SERIES);
                dto.setYear(participance.getYearStart());
                dto.setYearEnd(participance.getYearEnd());
            } else {
                dto.setParticipationType(ParticipationType.MOVIE);
                dto.setYear(participance.getYear());
            }
            
            dto.setTitle(participance.getTitle())
                .setOriginalTitle(participance.getOriginalTitle())
                .setDescription(participance.getSynopsisShort())
                .setReleaseDate(MetadataTools.parseToDate(participance.getReleaseDate()))
                .setReleaseCountry(participance.getReleaseCountry());
            
            result.add(dto);
        }
        
        return result;
    }
}
