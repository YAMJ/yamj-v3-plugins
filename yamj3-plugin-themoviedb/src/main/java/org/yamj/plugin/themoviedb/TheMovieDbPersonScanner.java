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

import com.omertron.themoviedbapi.model.person.PersonInfo;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.metadata.MetadataTools;
import org.yamj.plugin.api.metadata.PersonScanner;
import org.yamj.plugin.api.model.IPerson;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class TheMovieDbPersonScanner extends AbstractTheMovieDbScanner implements PersonScanner {

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
}
