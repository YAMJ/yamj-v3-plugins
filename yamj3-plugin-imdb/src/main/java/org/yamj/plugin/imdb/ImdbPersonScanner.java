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

import com.omertron.imdbapi.model.ImdbPerson;
import java.io.IOException;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.metadata.MetadataTools;
import org.yamj.plugin.api.metadata.PersonScanner;
import org.yamj.plugin.api.model.IPerson;
import org.yamj.plugin.api.web.HTMLTools;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class ImdbPersonScanner extends AbstractImdbScanner implements PersonScanner {

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
}
